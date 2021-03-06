/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.crypto.Keccak256;

/**
 * Created by oscar on 05/08/2016.
 */
public class PegTestUtils {

    public static void main(String[] args) {
        for (int i = 0; i < 257; i++) {
            createHash3();
        }
        Keccak256 hash = createHash3();
    }

    private static int nhash = 0;

    /**
     * @deprecated Use createHash3(int) instead. Avoid using persisted state in static class in test environments
     */
    @Deprecated
    public static Keccak256 createHash3() {
        return createHash3(nhash++);
    }

    public static Keccak256 createHash3(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) (nHash & 0xFF);
        bytes[1] = (byte) (nHash >>8 & 0xFF);
        return new Keccak256(bytes);
    }

    /**
     * @deprecated Use createHash(int) instead. Avoid using persisted state in static class in test environments
     */
    @Deprecated
    public static Sha256Hash createHash() {
        return createHash(nhash++);
    }

    public static Sha256Hash createHash(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) nHash;
        return Sha256Hash.wrap(bytes);
    }

    public static Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        Script scriptPubKey = federation.getP2SHScript();
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), redeemScript);
        Script inputScript = scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
        return inputScript;
    }

    public static Script createBaseRedeemScriptThatSpendsFromTheFederation(Federation federation) {
        Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getBtcPublicKeys());
        return redeemScript;
    }
}
