#include <iostream>
#include <string>
#include <vector>

class Service {
private:
    std::string name;
    int counter = 0;

public:
    int increment(int delta) {
        counter += delta;
        return counter;
    }

    int run() {
        int total = 0;
        for (int i = 0; i < 3; i++) {
            total += i;
        }
        if (total > 0) {
            total = total * 2;
        }
        return total;
    }
};

int main() {
    int total = 0;
    for (int i = 0; i < 3; i++) {
        total += i;
    }
    std::cout << "done";
    return 0;
}
