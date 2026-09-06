package de.dariatech.softphone

import android.app.Application

class SoftphoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LinphoneManager.init(this)
        /* DIE ANMELDUNG BEIM SYSTEM GEHÖRT AN DEN START, nicht an den
           ersten Anruf. Google verlangt die Telecom-Anbindung
           ausdrücklich „zu jeder Zeit, nicht nur wenn Android Auto
           läuft" – und ein Anruf, der erst beim Klingeln anmeldet,
           kommt für das Auto zu spät. Etappe 1, docs/AUFTRAG-AUTO.md. */
        Autoanruf.melde(this)
    }
}
