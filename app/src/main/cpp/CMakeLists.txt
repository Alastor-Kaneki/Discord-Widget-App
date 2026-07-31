cmake_minimum_required(VERSION 3.22.1)
project(discord_widget_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(discord_widget_bridge SHARED oauth_exchange_bridge.cpp)

find_package(discord_partner_sdk REQUIRED CONFIG)
find_library(log_lib log)

target_link_libraries(
    discord_widget_bridge
    discord_partner_sdk::discord_partner_sdk
    ${log_lib}
)
