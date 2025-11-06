#pragma once

#include <nlohmann/json.hpp>
#include <string>

namespace solus {

struct ParsedResponse {
  std::string action; // JSON string or empty
  std::string response;
};

class ResponseParser {
public:
  static ParsedResponse parse_response(const std::string &response_text);
};

} // namespace solus