# The pinned Android OpenSSL CMake glue lists ecp_sm2p256.c unconditionally.
# Upstream crypto/ec/build.info selects it only alongside an implementation of
# ECP_SM2P256_ASM (aarch64 among the Android ABIs we build). Its four BN_ULONG
# limbs and 32-byte copies are not a portable 32-bit implementation.
# Keep the generic SM2 implementation on other ABIs; do not alter crypto math.
function(ghajar_harden_openssl crypto_target android_abi)
    if(NOT android_abi MATCHES "^(armeabi-v7a|arm64-v8a|x86|x86_64)$")
        message(FATAL_ERROR "Review OpenSSL source selection for ABI: ${android_abi}")
    endif()

    get_target_property(crypto_sources ${crypto_target} SOURCES)
    get_target_property(crypto_definitions ${crypto_target} COMPILE_DEFINITIONS)
    set(sm2_asm_enabled FALSE)
    foreach(definition IN LISTS crypto_definitions)
        if(definition MATCHES "^(-D)?ECP_SM2P256_ASM(=1)?$")
            set(sm2_asm_enabled TRUE)
        endif()
    endforeach()
    if(android_abi STREQUAL "arm64-v8a")
        if(NOT sm2_asm_enabled)
            message(FATAL_ERROR "ARM64 SM2 source requires ECP_SM2P256_ASM")
        endif()
    elseif(sm2_asm_enabled)
        message(FATAL_ERROR "Unexpected ARM64 SM2 assembly on ${android_abi}")
    endif()

    set(optimized_source_count 0)
    foreach(source IN LISTS crypto_sources)
        if(source MATCHES "(^|/)ecp_sm2p256\\.c$")
            math(EXPR optimized_source_count "${optimized_source_count} + 1")
            if(NOT android_abi STREQUAL "arm64-v8a")
                list(REMOVE_ITEM crypto_sources "${source}")
            endif()
        endif()
    endforeach()
    if(NOT optimized_source_count EQUAL 1)
        message(FATAL_ERROR "Pinned OpenSSL source list changed; review the SM2 architecture guard")
    endif()
    set_property(TARGET ${crypto_target} PROPERTY SOURCES "${crypto_sources}")

    # A future accidental reintroduction must fail the Android Clang build.
    target_compile_options(${crypto_target} PRIVATE
        "$<$<COMPILE_LANG_AND_ID:C,Clang>:-Werror=fortify-source>")
endfunction()
