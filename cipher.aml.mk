#
# Copyright (C) 2026 The CipherOS Project
#
# Licensed under the CipherOS License 2.0. See the LICENSE file in the
# root of this repository for the full terms.
#

# CipherAML product makefile.
#
# Builds the CipherAML priv-app and installs everything it needs to work
# on system_ext: the privapp permission allowlist, the default runtime
# grants and the sysconfig entry for the location settings bypass.
#
# From your device or vendor product makefile:
#
#     $(call inherit-product, packages/apps/CipherAML/cipher.aml.mk)
#
# To ship your own endpoint table, set CIPHER_AML_ENDPOINTS *before* the
# inherit line:
#
#     CIPHER_AML_ENDPOINTS := device/acme/phone/aml/aml_endpoints.xml
#     $(call inherit-product, packages/apps/CipherAML/cipher.aml.mk)

# The XMLs are also pulled in by `required` in Android.bp. They are listed
# here too so the whole picture is visible from the product config.
PRODUCT_PACKAGES += \
    CipherAML \
    privapp-permissions-com.cipheros.cipheraml.xml \
    default-permissions-com.cipheros.cipheraml.xml \
    cipheraml-sysconfig.xml

ifneq ($(strip $(CIPHER_AML_ENDPOINTS)),)
ifeq ($(wildcard $(CIPHER_AML_ENDPOINTS)),)
$(error CIPHER_AML_ENDPOINTS points at $(CIPHER_AML_ENDPOINTS), which does not exist)
endif
PRODUCT_COPY_FILES += \
    $(CIPHER_AML_ENDPOINTS):$(TARGET_COPY_OUT_PRODUCT)/etc/cipheraml/aml_endpoints.xml
endif
