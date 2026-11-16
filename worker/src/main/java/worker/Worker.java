package worker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.json.JSONObject;
import org.json.JSONException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.args.ListDirection;
import redis.clients.jedis.exceptions.JedisConnectionException;

class Worker {
  static final String QUEUE = "votes";
  static final String PROCESSING = "votes:processing";

  public static void main(String[] args) {
    String redisHost = env("REDIS_HOST", "localhost");
    String dbHost = env("DB_HOST", "localhost");
    String dbName = env("POSTGRES_DB", "postgres");
    String dbUser = env("POSTGRES_USER", "postgres");
    String dbPassword = env("POSTGRES_PASSWORD", "password");

    try {
      Jedis redis = connectToRedis(redisHost);
      Connection dbConn = connectToDB(dbHost, dbName, dbUser, dbPassword);

      requeueUnfinished(redis);
      System.err.println("Watching vote queue");

      while (true) {
        // BLMOVE keeps the vote in a processing list until it is stored,
        // so a crash between the pop and the insert does not lose it
        String voteJSON = redis.blmove(QUEUE, PROCESSING, ListDirection.LEFT, ListDirection.RIGHT, 0);
        try {
          JSONObject voteData = new JSONObject(voteJSON);
          String voterID = voteData.getString("voter_id");
          String vote = voteData.getString("vote");

          System.err.printf("Processing vote for '%s' by '%s'%n", vote, voterID);
          updateVote(dbConn, voterID, vote);
        } catch (JSONException e) {
          System.err.println("Dropping malformed message: " + voteJSON);
        }
        redis.lrem(PROCESSING, 1, voteJSON);
      }
    } catch (SQLException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  // Votes left in the processing list by a previous run go back to the
  // front of the queue, oldest first
  static void requeueUnfinished(Jedis redis) {
    int count = 0;
    while (redis.lmove(PROCESSING, QUEUE, ListDirection.RIGHT, ListDirection.LEFT) != null) {
      count++;
    }
    if (count > 0) {
      System.err.printf("Requeued %d unfinished vote(s)%n", count);
    }
  }

  static void updateVote(Connection dbConn, String voterID, String vote) throws SQLException {
    try (PreparedStatement upsert = dbConn.prepareStatement(
        "INSERT INTO votes (id, vote) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET vote = EXCLUDED.vote")) {
      upsert.setString(1, voterID);
      upsert.setString(2, vote);
      upsert.executeUpdate();
    }
  }

  static Jedis connectToRedis(String host) {
    Jedis conn = new Jedis(host, 6379);

    while (true) {
      try {
        conn.ping();
        break;
      } catch (JedisConnectionException e) {
        System.err.println("Waiting for redis");
        sleep(1000);
      }
    }

    System.err.println("Connected to redis");
    return conn;
  }

  static Connection connectToDB(String host, String name, String user, String password) {
    String url = "jdbc:postgresql://" + host + "/" + name;
    Connection conn = null;

    while (conn == null) {
      try {
        conn = DriverManager.getConnection(url, user, password);
      } catch (SQLException e) {
        System.err.println("Waiting for db");
        sleep(1000);
      }
    }

    System.err.println("Connected to db");
    return conn;
  }

  static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isEmpty() ? fallback : value;
  }

  static void sleep(long duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      System.exit(1);
    }
  }
}
