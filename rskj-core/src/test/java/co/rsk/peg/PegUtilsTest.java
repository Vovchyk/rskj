package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static co.rsk.peg.PegTestUtils.createFederation;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PegUtilsTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final Context context = new Context(bridgeMainnetConstants.getBtcParams());
    ActivationConfig.ForBlock activations = ActivationConfigsForTest.tbd600().forBlock(0);

    BridgeStorageProvider provider;

    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;

    @BeforeEach
    void init() {
        provider = mock(BridgeStorageProvider.class);
    }

    @Test()
    void test_getTransactionType_before_tbd_600() {
        // Arrange
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        Wallet liveFederationWallet = mock(Wallet.class);
        BtcTransaction btcTransaction = mock(BtcTransaction.class);

        // Act
        RegisterBtcTransactionException exception = Assertions.assertThrows(RegisterBtcTransactionException.class, () -> {
            PegUtils.getTransactionType(fingerrootActivations, provider, liveFederationWallet, btcTransaction);
        });

        // Assert
        String expectedMessage = "Can't call PegUtil.getTransactionType before RSKIP379 activation.";
        Assertions.assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void test_getTransactionType_tx_sending_funds_to_unknown_address() throws RegisterBtcTransactionException {
        // Arrange
        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        Address unknownAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "unknown");

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(minimumPeginTxValue.minus(Coin.SATOSHI), unknownAddress);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_below_minimum_active_fed() throws RegisterBtcTransactionException {
        // Arrange
        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(minimumPeginTxValue.minus(Coin.SATOSHI), activeFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_active_fed() throws RegisterBtcTransactionException {
        // Arrange
        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_retiring_fed() throws RegisterBtcTransactionException {
        // Arrange
        Federation retiringFed = bridgeMainnetConstants.getGenesisFederation();

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        P2shErpFederation activeFed = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(signers),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams,
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay(),
            activations
        );
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFed, activeFed));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(minimumPeginTxValue, retiringFed.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegout_no_change_output() throws RegisterBtcTransactionException {
        // Arrange
        Federation activeFed = bridgeMainnetConstants.getGenesisFederation();

        Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");

        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFed));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(activeFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, activeFed.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFed.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void test_getTransactionType_pegout_no_change_output_sighash_no_exists_in_provider() throws RegisterBtcTransactionException {
        // Arrange
        Federation activeFed = bridgeMainnetConstants.getGenesisFederation();

        Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");

        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFed));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(activeFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, activeFed.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @Test
    void test_getTransactionType_standard_pegout() throws RegisterBtcTransactionException {
        // Arrange
        Federation activeFed = bridgeMainnetConstants.getGenesisFederation();

        Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");

        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFed));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, activeFed.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(activeFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, activeFed.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFed.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void test_getTransactionType_standard_pegout_sighash_no_exists_in_provider() throws RegisterBtcTransactionException {
        // Arrange
        Federation activeFed = bridgeMainnetConstants.getGenesisFederation();

        Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");

        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFed));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, activeFed.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(activeFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, activeFed.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_migration() throws RegisterBtcTransactionException {
        // Arrange
        Federation retiringFed = bridgeMainnetConstants.getGenesisFederation();

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        P2shErpFederation activeFed = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(signers),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams,
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay(),
            activations
        );
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFed, activeFed));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Sha256Hash fundTxHash = BitcoinTestUtils.createHash(1);
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 1, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 2, new Script(new byte[]{}));

        btcTransaction.addOutput(Coin.COIN, activeFed.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFed.getRedeemScript());
        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFed.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );
        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void test_getTransactionType_migration_sighash_no_exists_in_provider() throws RegisterBtcTransactionException {
        // Arrange
        Federation retiringFed = bridgeMainnetConstants.getGenesisFederation();

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        P2shErpFederation activeFed = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(signers),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams,
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay(),
            activations
        );
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFed, activeFed));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Sha256Hash fundTxHash = BitcoinTestUtils.createHash(1);
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 1, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 2, new Script(new byte[]{}));

        btcTransaction.addOutput(Coin.COIN, activeFed.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFed.getRedeemScript());
        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_migration_from_retired_fed() throws RegisterBtcTransactionException {
        // Arrange
        Federation activeFed = bridgeMainnetConstants.getGenesisFederation();
        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        Federation retiredFed = createFederation(bridgeMainnetConstants, signers);

        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFed));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, retiredFed.getP2SHScript());
        btcTransaction.addOutput(minimumPeginTxValue, activeFed.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiredFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiredFed.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiredFed.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void test_getTransactionType_flyover() throws RegisterBtcTransactionException {
        // Arrange
        Federation activeFed = bridgeMainnetConstants.getGenesisFederation();
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFed));

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = new BridgeSupportBuilder().build();
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            activeFed.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTransaction.addOutput(Coin.COIN, activeFederationAddress);

        btcTransaction.addInput(
            Sha256Hash.ZERO_HASH,
            0, new Script(new byte[]{})
        );

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @Test
    void test_getTransactionType_flyover_segwit() throws RegisterBtcTransactionException {
        // Arrange
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();
        NetworkParameters btcTestNetParams = bridgeTestNetConstants.getBtcParams();
        Context context = new Context(bridgeTestNetConstants.getBtcParams());

        Federation retiringFed = bridgeTestNetConstants.getGenesisFederation();

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        P2shErpFederation activeFed = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(signers),
            Instant.ofEpochMilli(1000L),
            0L,
            btcTestNetParams,
            bridgeTestNetConstants.getErpFedPubKeysList(),
            bridgeTestNetConstants.getErpFedActivationDelay(),
            activations
        );
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFed, activeFed));

        String segwitTxHex = "020000000001011f668117f2ca3314806ade1d99ae400f5413d7e9d4bfcbd11d52645e060e22fb0100000000fdffffff0300000000000000001b6a1952534b5401a27c6f697954357247e78f9900023cfe01a9d49c0412030000000000160014b413f59a7ee6e34321140e83ea661e0484a79bc2988708000000000017a9145e6cf80958803e9b3c81cd90422152520d2a505c870247304402203fce49b39f79581d93720f462b5f33f9174e66dc6efb635d4f41aacb33b08d0302201221aec5db31e269454fcc7a4df2936ccedd566ccf48828d4f97050954f196540121021831c5ba44b739521d635e521560525672087e4d5db053801f4aeb60e782f6d6d0f02400";
        BtcTransaction btcTransaction = new BtcTransaction(btcTestNetParams, Hex.decode(segwitTxHex));

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }
}
