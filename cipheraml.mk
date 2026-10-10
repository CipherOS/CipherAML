#
# Copyright (C) 2026 The CipherOS Project
#
# Licensed under the CipherOS License 2.0. See the LICENSE file in the
# root of this repository for the full terms.
#

PRODUCT_PACKAGES += \
    CipherAML

PRODUCT_COPY_FILES += \
    packages/apps/CipherAML/etc/permissions/privapp-permissions-com.cipheros.cipheraml.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-com.cipheros.cipheraml.xml \
    packages/apps/CipherAML/etc/default-permissions/default-permissions-com.cipheros.cipheraml.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/default-permissions/default-permissions-com.cipheros.cipheraml.xml \
    packages/apps/CipherAML/etc/sysconfig/cipheraml.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/sysconfig/cipheraml.xml
