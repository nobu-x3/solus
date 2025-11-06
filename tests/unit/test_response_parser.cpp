#include "server/response_parser.h"
#include "utils/helpers.h"
#include <gtest/gtest.h>

namespace solus::test {

class ResponseParserTest : public ::testing::Test {
protected:
  ResponseParser parser;
};

TEST_F(ResponseParserTest, ParseActionWithResponse) {
  std::string response = R"({
        "action": {
            "type": "todo_add",
            "params": {"title": "test"}
        },
        "response": "Done!"
    })";
  auto parsed = ResponseParser::parse_response(response);
  EXPECT_FALSE(parsed.action.empty());
  EXPECT_EQ(parsed.response, "Done!");
  auto action_json = nlohmann::json::parse(parsed.action);
  EXPECT_EQ(action_json["type"], "todo_add");
}

TEST_F(ResponseParserTest, ParseActionOnly) {
  std::string response =
      R"(Some text {"action": {"type": "app_open", "params": {"package": "com.test"}}} more text)";
  auto parsed = ResponseParser::parse_response(response);
  EXPECT_FALSE(parsed.action.empty());
  EXPECT_TRUE(StringUtils::contains(parsed.response, "Some text"));
  EXPECT_TRUE(StringUtils::contains(parsed.response, "more text"));
}

TEST_F(ResponseParserTest, ParseTextOnly) {
  std::string response = "This is just a normal response without actions.";
  auto parsed = ResponseParser::parse_response(response);
  EXPECT_TRUE(parsed.action.empty());
  EXPECT_EQ(parsed.response, response);
}

TEST_F(ResponseParserTest, ParseInvalidJson) {
  std::string response = "Text with {invalid json} in it";
  auto parsed = ResponseParser::parse_response(response);
  EXPECT_TRUE(parsed.action.empty());
  EXPECT_EQ(parsed.response, response);
}

TEST_F(ResponseParserTest, ParseEmptyResponse) {
  std::string response = "";
  auto parsed = ResponseParser::parse_response(response);
  EXPECT_TRUE(parsed.action.empty());
  EXPECT_TRUE(parsed.response.empty());
}

TEST_F(ResponseParserTest, ParseNestedJson) {
  std::string response = R"({
        "action": {
            "type": "reminder_set",
            "params": {
                "title": "Meeting",
                "time": "2024-01-01T10:00:00",
                "nested": {"key": "value"}
            }
        },
        "response": "Reminder set"
    })";
  auto parsed = ResponseParser::parse_response(response);
  EXPECT_FALSE(parsed.action.empty());
  auto action_json = nlohmann::json::parse(parsed.action);
  EXPECT_TRUE(action_json["params"].contains("nested"));
}

} // namespace solus::test