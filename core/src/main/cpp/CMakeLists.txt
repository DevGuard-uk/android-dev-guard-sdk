cmake_minimum_required(VERSION 3.18.1)
project("devguard_sdk")

add_library(
    devguard_sdk
    SHARED
    devguard_bridge.cpp
    devguard_core.c
)

target_include_directories(devguard_sdk PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)

target_link_libraries(devguard_sdk ${log-lib})
