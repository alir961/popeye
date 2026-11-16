import json
import os
import secrets
import socket

from flask import Flask, g, make_response, render_template, request
from redis import Redis

option_a = os.getenv("OPTION_A", "Ansible")
option_b = os.getenv("OPTION_B", "Chef")
option_c = os.getenv("OPTION_C", "Puppet")
option_d = os.getenv("OPTION_D", "SaltStack")
redis_host = os.getenv("REDIS_HOST", "localhost")
hostname = socket.gethostname()

VALID_VOTES = {"a", "b", "c", "d"}

app = Flask(__name__)


def get_redis():
    if "redis" not in g:
        g.redis = Redis(host=redis_host, db=0, socket_timeout=5)
    return g.redis


@app.route("/", methods=["POST", "GET"])
def hello():
    voter_id = request.cookies.get("voter_id") or secrets.token_hex(8)

    vote = None

    if request.method == "POST":
        vote = request.form.get("vote")
        if vote not in VALID_VOTES:
            return "Invalid vote", 400
        data = json.dumps({"voter_id": voter_id, "vote": vote})
        get_redis().rpush("votes", data)

    resp = make_response(render_template(
        "index.html",
        option_a=option_a,
        option_b=option_b,
        option_c=option_c,
        option_d=option_d,
        hostname=hostname,
        vote=vote,
    ))
    resp.set_cookie("voter_id", voter_id, httponly=True, samesite="Lax")
    return resp


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080, debug=True, threaded=True)
