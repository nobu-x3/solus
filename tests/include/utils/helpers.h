#pragma once

#include <filesystem>
#include <fstream>
#include <gtest/gtest.h>
#include <nlohmann/json.hpp>
#include <string>
#include <vector>

namespace solus::test {

class TempDirectory {
public:
  TempDirectory() {
    path_ = std::filesystem::temp_directory_path() /
            ("solus_test_" + std::to_string(rand()));
    std::filesystem::create_directories(path_);
  }

  ~TempDirectory() {
    if (std::filesystem::exists(path_)) {
      std::filesystem::remove_all(path_);
    }
  }

  std::string path() const { return path_.string(); }

private:
  std::filesystem::path path_;
};

// Mock file creator
class MockFileCreator {
public:
  static void create_json_file(const std::string &path,
                               const nlohmann::json &content) {
    std::ofstream out(path);
    out << content.dump(2);
  }

  static void create_bin_file(const std::string &path, size_t size) {
    std::ofstream out(path, std::ios::binary);
    std::vector<char> data(size, 0);
    out.write(data.data(), size);
  }
};

template <typename T>
bool vectors_eq(const std::vector<T> &a, const std::vector<T> &b,
                T epsilon = 1e-6) {
  if (a.size() != b.size())
    return false;
  for (size_t i = 0; i < a.size(); i++) {
    if (std::abs(a[i] - b[i]) > epsilon)
      return false;
  }
  return true;
}

class JsonMatcher {
public:
  static bool has_key(const nlohmann::json &j, const std::string &key) {
    return j.contains(key);
  }

  static bool has_keys(const nlohmann::json &j,
                       const std::vector<std::string> &keys) {
    for (const auto &key : keys) {
      if (!j.contains(key))
        return false;
    }
    return true;
  }

  static bool matches_schema(const nlohmann::json &j,
                             const nlohmann::json &schema) {
    for (auto &[key, value] : schema.items()) {
      if (!j.contains(key))
        return false;
      if (j[key].type() != value.type())
        return false;
    }
    return true;
  }
};

class StringUtils {
public:
  static bool contains(const std::string &str, const std::string &substr) {
    return str.find(substr) != std::string::npos;
  }

  static std::string trim(const std::string &str) {
    auto start = str.find_first_not_of(" \t\n\r");
    auto end = str.find_last_not_of(" \t\n\r");
    if (start == std::string::npos)
      return "";
    return str.substr(start, end - start + 1);
  }
};

class RandomGenerator {
public:
  static std::vector<float> embedding(size_t dim) {
    std::vector<float> result(dim);
    for (size_t i = 0; i < dim; i++) {
      result[i] = static_cast<float>(rand()) / RAND_MAX;
    }
    float norm = 0.0f;
    for (float v : result)
      norm += v * v;
    norm = std::sqrt(norm);
    for (float &v : result)
      v /= norm;
    return result;
  }

  static std::string string(size_t length) {
    static const char chars[] = "abcdefghijklmnopqrstuvwxyz0123456789";
    std::string result;
    result.reserve(length);
    for (size_t i = 0; i < length; i++) {
      result += chars[rand() % (sizeof(chars) - 1)];
    }
    return result;
  }

  static int64_t timestamp() { return std::time(nullptr) + (rand() % 86400); }
};

class Timer {
public:
  void start() { m_Start = std::chrono::high_resolution_clock::now(); }

  double elapsedMs() const {
    auto end = std::chrono::high_resolution_clock::now();
    return std::chrono::duration<double, std::milli>(end - m_Start).count();
  }

private:
  std::chrono::high_resolution_clock::time_point m_Start;
};

} // namespace solus::test