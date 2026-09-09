cmake_minimum_required(VERSION 3.22)
project(GhajarNativeArchRegression LANGUAGES C)
enable_testing()

include("${CMAKE_CURRENT_LIST_DIR}/../../openvpn/src/main/cpp/ghajar-openssl-arch.cmake")
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/generic_sm2.c" "int ghajar_generic_sm2(void) { return 1; }\n")
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/ecp_sm2p256.c" "int ghajar_optimized_sm2(void) { return 1; }\n")

foreach(abi armeabi-v7a arm64-v8a x86 x86_64)
    string(REPLACE "-" "_" suffix "${abi}")
    set(target "crypto_${suffix}")
    add_library(${target} STATIC
        "${CMAKE_CURRENT_BINARY_DIR}/generic_sm2.c"
        "${CMAKE_CURRENT_BINARY_DIR}/ecp_sm2p256.c")
    if(abi STREQUAL "arm64-v8a")
        target_compile_definitions(${target} PRIVATE ECP_SM2P256_ASM)
    endif()
    ghajar_harden_openssl(${target} "${abi}")
    get_target_property(selected ${target} SOURCES)
    list(FIND selected "${CMAKE_CURRENT_BINARY_DIR}/generic_sm2.c" generic_index)
    list(FIND selected "${CMAKE_CURRENT_BINARY_DIR}/ecp_sm2p256.c" optimized_index)
    if(generic_index LESS 0)
        message(FATAL_ERROR "Generic SM2 was removed for ${abi}")
    endif()
    if(abi STREQUAL "arm64-v8a")
        if(optimized_index LESS 0)
            message(FATAL_ERROR "ARM64 lost its optimized implementation")
        endif()
    elseif(NOT optimized_index LESS 0)
        message(FATAL_ERROR "ARM64-only implementation leaked into ${abi}")
    endif()
    add_test(NAME "select_${abi}" COMMAND "${CMAKE_COMMAND}" -E true)
endforeach()

# Run malformed configurations separately and require them to be rejected.
foreach(case unknown_abi wrong_asm missing_asm missing_source)
    add_test(NAME "reject_${case}" COMMAND "${CMAKE_COMMAND}"
        -S "${CMAKE_CURRENT_LIST_DIR}/invalid"
        -B "${CMAKE_CURRENT_BINARY_DIR}/invalid_${case}"
        "-DCASE=${case}")
    set_tests_properties("reject_${case}" PROPERTIES WILL_FAIL TRUE)
endforeach()
