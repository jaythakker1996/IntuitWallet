package com.intuit.walletservice.businesslogic.core;

import java.util.UUID;

public final class SystemWallets {

    public static final UUID EXTERNAL_DEPOSITS    = UUID.fromString("00000000-0000-0000-0000-00000000ed01");
    public static final UUID EXTERNAL_WITHDRAWALS = UUID.fromString("00000000-0000-0000-0000-00000000ed02");
    public static final UUID FEE_REVENUE          = UUID.fromString("00000000-0000-0000-0000-00000000ed03");
    public static final UUID TREASURY             = UUID.fromString("00000000-0000-0000-0000-00000000ed04");

    private SystemWallets() {
    }
}
