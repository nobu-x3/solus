cmake_minimum_required(VERSION 3.20)
project(solus_server CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -pthread")

option(BUILD_SHARED_LIBS "Build shared libraries" OFF)

include_directories(
    ${CMAKE_SOURCE_DIR}/include
    ${CMAKE_SOURCE_DIR}/vendor/sap_http/include
)

set(SOLUS_SOURCES
    src/main.cpp
)

add_executable(solus_server ${SOLUS_SOURCES})

set(SAP_HTTP_BUILD_TESTS Off)
set(SAP_HTTP_INSTALL Off)

if(BUILD_SHARED_LIBS)
    set(SAP_HTTP_BUILD_SHARED On)
    set(SAP_HTTP_BUILD_STATIC Off)
else()
    set(SAP_HTTP_BUILD_SHARED Off)
    set(SAP_HTTP_BUILD_STATIC On)
endif()

add_subdirectory(vendor/sap_http)

target_link_libraries(solus_server
    PRIVATE
    sap::http
)

message(STATUS "")
message(STATUS "========================================")
message(STATUS "Solus Server Configuration")
message(STATUS "========================================")
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C++ compiler: ${CMAKE_CXX_COMPILER}")
message(STATUS "C++ flags: ${CMAKE_CXX_FLAGS}")
message(STATUS "========================================")
message(STATUS "")