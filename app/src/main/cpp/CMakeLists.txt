cmake_minimum_required(VERSION 3.22.1)
project(discord_widget_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(discord_widget_bridge SHARED social_bridge.cpp)

target_include_directories(discord_widget_bridge PRIVATE "${DISCORD_SDK_ROOT}/include")

add_library(discord_partner_sdk SHARED IMPORTED)
set_target_properties(
    discord_partner_sdk
    PROPERTIES
    IMPORTED_LOCATION "${DISCORD_SDK_ROOT}/lib/${ANDROID_ABI}/libdiscord_partner_sdk.so"
)

find_library(log_lib log)

target_link_libraries(discord_widget_bridge discord_partner_sdk ${log_lib})
