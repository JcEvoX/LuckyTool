package com.luckyzyx.luckytool.hook.scopes.systemui

import android.os.Message
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import org.lsposed.lsparanoid.Obfuscate

@Obfuscate
object RemoveStatusBarSecurePayment : YukiBaseHooker() {
    override fun onHook() {
        //Source SecurePaymentController
        VariousClass(
            "com.oplus.systemui.statusbar.phone.securepay.SecurePaymentControllerExImpl", //C12 C13
            "com.oplus.systemui.statusbar.phone.dynamic.SecurePaymentController", //C14
            "com.oplus.systemui.common.manager.OplusSystemUiManagerExImpl" //C17
        ).toClass().resolve().apply {
            firstMethod {
                name { it == "handlePaymentDetectionMessage" || it == "access\$handlePaymentDetectionMessage" }
                parameters { it.lastOrNull() == Message::class.java && it.size in 1..2 }
            }.hook {
                intercept()
            }
        }
    }
}