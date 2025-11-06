#include "server/response_parser.h"

namespace solus {

ParsedResponse ResponseParser::parse_response(const std::string &response_text) {
  ParsedResponse result;
  result.response = response_text;
  // Try to extract JSON
  size_t json_start = response_text.find('{');
  size_t json_end = response_text.rfind('}');
  if (json_start != std::string::npos && json_end != std::string::npos &&
      json_end > json_start) {
    try {
      std::string json_str =
          response_text.substr(json_start, json_end - json_start + 1);
      auto parsed = nlohmann::json::parse(json_str);
      if (parsed.contains("action") && parsed.contains("response")) {
        result.action = parsed["action"].dump();
        result.response = parsed["response"].get<std::string>();
      } else if (parsed.contains("action")) {
        result.action = parsed["action"].dump();
        // Response is text outside JSON
        std::string text_response = response_text.substr(0, json_start) +
                                    response_text.substr(json_end + 1);
        result.response = text_response.empty() ? "Done." : text_response;
      }
    } catch (const nlohmann::json::exception &) {
      // JSON parsing failed, treat entire response as text
    }
  }
  return result;
}

} // namespace solus