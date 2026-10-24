"""Redis hot store for the latest rider positions and per-order route cache."""
import json

from django_redis import get_redis_connection

LOC_TTL = 600  # seconds a location is considered live
ROUTE_TTL = 1800
HISTORY_BUFFER_KEY = "lr:loc:buffer"


class _MemoryRedis:
    """Process-local stand-in used when the cache backend is not Redis (unit tests / local dev without Redis)."""

    def __init__(self):
        self.kv, self.lists, self.geo = {}, {}, {}

    def set(self, k, v, ex=None):
        self.kv[k] = v

    def get(self, k):
        return self.kv.get(k)

    def delete(self, k):
        self.kv.pop(k, None)

    def geoadd(self, key, values):
        self.geo[values[2]] = values[:2]

    def zrem(self, key, member):
        self.geo.pop(member, None)

    def rpush(self, k, v):
        self.lists.setdefault(k, []).append(v)

    def pipeline(self):
        outer = self

        class _Pipe:
            def __init__(self):
                self.ops = []

            def lrange(self, k, a, b):
                self.ops.append(("lrange", k, a, b))

            def ltrim(self, k, a, b):
                self.ops.append(("ltrim", k, a, b))

            def execute(self):
                out = []
                for op in self.ops:
                    if op[0] == "lrange":
                        out.append(outer.lists.get(op[1], [])[op[2]:op[3] + 1])
                    else:
                        outer.lists[op[1]] = outer.lists.get(op[1], [])[op[2]:]
                        out.append(True)
                return out

        return _Pipe()


_memory = _MemoryRedis()


def _r():
    try:
        return get_redis_connection("default")
    except NotImplementedError:
        return _memory


def set_rider_location(rider_id, data: dict):
    r = _r()
    r.set(f"lr:rider:{rider_id}:loc", json.dumps(data), ex=LOC_TTL)
    r.geoadd("lr:riders:geo", (data["longitude"], data["latitude"], str(rider_id)))


def get_rider_location(rider_id) -> dict | None:
    raw = _r().get(f"lr:rider:{rider_id}:loc")
    return json.loads(raw) if raw else None


def remove_rider(rider_id):
    r = _r()
    r.delete(f"lr:rider:{rider_id}:loc")
    r.zrem("lr:riders:geo", str(rider_id))


def push_history(entry: dict):
    _r().rpush(HISTORY_BUFFER_KEY, json.dumps(entry))


def drain_history(max_items=5000) -> list[dict]:
    r = _r()
    pipe = r.pipeline()
    pipe.lrange(HISTORY_BUFFER_KEY, 0, max_items - 1)
    pipe.ltrim(HISTORY_BUFFER_KEY, max_items, -1)
    items, _ = pipe.execute()
    return [json.loads(i) for i in items]


def set_order_route(order_id, route: dict):
    _r().set(f"lr:order:{order_id}:route", json.dumps(route), ex=ROUTE_TTL)


def get_order_route(order_id) -> dict | None:
    raw = _r().get(f"lr:order:{order_id}:route")
    return json.loads(raw) if raw else None


def clear_order_route(order_id):
    _r().delete(f"lr:order:{order_id}:route")
