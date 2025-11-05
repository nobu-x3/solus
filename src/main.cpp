#include <csignal>
#include <iostream>
#include <net/http.h>

void signal_handler(int signal) {
  if (signal == SIGINT || signal == SIGTERM) {
    std::cout << "\nShutting down gracefully..." << std::endl;
    exit(0);
  }
}

int main() {
  std::signal(SIGINT, signal_handler);
  std::signal(SIGTERM, signal_handler);
  try {
    http::ServerConfig cfg{};
    http::Server srv{std::move(cfg)};
  } catch (const std::exception &e) {
    std::cerr << "Fatal error: " << e.what() << std::endl;
    return 1;
  }
  return 0;
}