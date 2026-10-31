package dev.remotegpt.client

import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/** Android's stripped BC provider lacks the algorithms required by SSHJ. */
internal object SshCrypto {
    private var configured = false
    @Synchronized fun configure() {
        if (configured) return
        val existing = Security.getProvider("BC")
        if (existing?.javaClass?.name?.startsWith("com.android.") == true) Security.removeProvider("BC")
        if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
        SecurityUtils.setSecurityProvider("BC")
        configured = true
    }
}
