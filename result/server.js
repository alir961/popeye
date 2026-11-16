const express = require('express');
const http = require('http');
const path = require('path');
const { Pool } = require('pg');
const { Server } = require('socket.io');

const port = process.env.PORT || 8080;

const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  database: process.env.POSTGRES_DB || 'postgres',
  user: process.env.POSTGRES_USER || 'postgres',
  password: process.env.POSTGRES_PASSWORD || 'password',
});

const app = express();
const server = http.createServer(app);
const io = new Server(server);

io.on('connection', function (socket) {
  socket.emit('message', { text: 'Welcome!' });
});

async function publishScores() {
  try {
    const result = await pool.query('SELECT vote, COUNT(id) AS count FROM votes GROUP BY vote');
    io.emit('scores', JSON.stringify(collectVotesFromResult(result)));
  } catch (err) {
    console.error('Error performing query: ' + err.message);
  }

  setTimeout(publishScores, 1000);
}

function collectVotesFromResult(result) {
  const votes = { a: 0, b: 0, c: 0, d: 0 };

  result.rows.forEach(function (row) {
    votes[row.vote] = parseInt(row.count, 10);
  });

  return votes;
}

app.use(express.static(path.join(__dirname, 'views')));

app.get('/', function (req, res) {
  res.sendFile(path.join(__dirname, 'views', 'index.html'));
});

server.listen(port, function () {
  console.log('App running on port ' + server.address().port);
  publishScores();
});
