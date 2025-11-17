#include "server/config.h"
#include <csignal>
#include <iostream>
#include <net/http.h>
#include "server/solus_server.h"

void signal_handler(int signal) {
  if (signal == SIGINT || signal == SIGTERM) {
    std::cout << "\nShutting down gracefully..." << std::endl;
    exit(0);
  }
}

void print_usage(const char *program_name) {
  std::cout << "Usage: " << program_name << " [options]\n"
            << "Options:\n"
            << "  --model PATH         Path to GGUF model file\n"
            << "  --port PORT          Server port (default: 8000)\n"
            << "  --threads N          Number of CPU threads (default: 16)\n"
            << "  --gpu-layers N       GPU layers to offload (default: 33)\n"
            << "  --ctx-size N         Context size (default: 4096)\n"
            << "  --temperature F      Generation temperature (default: 0.7)\n"
            << "  --help               Show this help message\n";
}

int main(int argc, char **argv) {
  solus::ServerConfig config;
  // Parse command line arguments
  for (int i = 1; i < argc; i++) {
    std::string arg = argv[i];

    if (arg == "--help" || arg == "-h") {
      print_usage(argv[0]);
      return 0;
    } else if (arg == "--model" && i + 1 < argc) {
      config.model_path = argv[++i];
    } else if (arg == "--port" && i + 1 < argc) {
      config.port = std::stoi(argv[++i]);
    } else if (arg == "--threads" && i + 1 < argc) {
      config.n_threads = std::stoi(argv[++i]);
    } else if (arg == "--gpu-layers" && i + 1 < argc) {
      config.n_gpu_layers = std::stoi(argv[++i]);
    } else if (arg == "--ctx-size" && i + 1 < argc) {
      config.n_ctx = std::stoi(argv[++i]);
    } else if (arg == "--temperature" && i + 1 < argc) {
      config.temperature = std::stof(argv[++i]);
    } else {
      std::cerr << "Unknown option: " << arg << std::endl;
      print_usage(argv[0]);
      return 1;
    }
  }
  if (config.model_path.empty()) {
    std::cerr << "Error: Model path is required (--model)" << std::endl;
    return 1;
  }
  std::signal(SIGINT, signal_handler);
  std::signal(SIGTERM, signal_handler);
  std::cout << "========================================\n"
            << "Solus AI Assistant Server\n"
            << "========================================\n"
            << "Model: " << config.model_path << "\n"
            << "Port: " << config.port << "\n"
            << "Threads: " << config.n_threads << "\n"
            << "GPU Layers: " << config.n_gpu_layers << "\n"
            << "Context Size: " << config.n_ctx << "\n"
            << "Temperature: " << config.temperature << "\n"
            << "========================================\n"
            << std::endl;
  try {
    solus::SolusServer server(std::move(config));
    if (!server.initialize()) {
      std::cerr << "Failed to initialize server" << std::endl;
      return 1;
    }
    server.run();
  } catch (const std::exception &e) {
    std::cerr << "Fatal error: " << e.what() << std::endl;
    return 1;
  }
  return 0;
}