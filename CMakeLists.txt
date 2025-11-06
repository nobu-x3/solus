cmake_minimum_required(VERSION 3.20)
project(solus_server CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -pthread")

option(GGML_HIPBLAS "Enable hipBLAS support for AMD GPUs" OFF)
option(BUILD_SHARED_LIBS "Build shared libraries" OFF)

# ROCm/HIP support for AMD GPU
if(GGML_HIPBLAS)
    list(APPEND CMAKE_PREFIX_PATH /opt/rocm)
    find_package(hip REQUIRED)
    find_package(hipblas REQUIRED)
    find_package(rocblas REQUIRED)

    add_compile_definitions(GGML_USE_HIPBLAS)
    message(STATUS "ROCm found: ${hip_VERSION}")
    message(STATUS "hipBLAS enabled")
endif()

include_directories(
    ${CMAKE_SOURCE_DIR}/include
    ${CMAKE_SOURCE_DIR}/vendor/sap_http/include
    ${CMAKE_SOURCE_DIR}/vendor/hnswlib/hnswlib
    ${CMAKE_SOURCE_DIR}/vendor/llama.cpp/include
    ${CMAKE_SOURCE_DIR}/vendor/json/include
)

set(SOLUS_SOURCES
    src/main.cpp
    src/memory/database.cpp
    src/server/prompt_builder.cpp
    src/server/response_parser.cpp
    src/llm/llama_handler.cpp
)

add_executable(solus_server ${SOLUS_SOURCES})

set(HNSWLIB_EXAMPLES Off)
add_subdirectory(vendor/hnswlib)

set(JSON_BuildTests Off)
add_subdirectory(vendor/json)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
add_subdirectory(vendor/llama.cpp)

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
    hnswlib::hnswlib
    llama
    nlohmann_json::nlohmann_json
)

if(GGML_HIPBLAS)
    target_link_libraries(solus_server
        hip::host
        roc::hipblas
        roc::rocblas
    )
endif()

option(BUILD_TESTS "Build tests" OFF)

if(BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()

message(STATUS "")
message(STATUS "========================================")
message(STATUS "Solus Server Configuration")
message(STATUS "========================================")
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C++ compiler: ${CMAKE_CXX_COMPILER}")
message(STATUS "C++ standard: ${CMAKE_CXX_STANDARD}")
message(STATUS "C++ flags: ${CMAKE_CXX_FLAGS}")
message(STATUS "Build tests: ${BUILD_TESTS}")
message(STATUS "Install prefix: ${CMAKE_INSTALL_PREFIX}")
message(STATUS "========================================")
message(STATUS "")