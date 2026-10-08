"""Valid complete Python program fixture."""
import math


class Service:
    def __init__(self, name: str) -> None:
        self.name = name
        self.counter = 0

    def increment(self, delta: int) -> int:
        self.counter += delta
        return self.counter

    def process(self, items: list) -> list:
        results = []
        for item in items:
            if item is not None and len(item) > 0:
                results.append(item.strip())
        return results


def main() -> None:
    svc = Service("demo")
    print(svc.process([" a ", "", "b"]))


if __name__ == "__main__":
    main()
