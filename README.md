# MassaPay - Secure Massa Blockchain Wallet

<div align="center">

![MassaPay Logo](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

**Your keys. Your crypto. Your freedom.**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Version](https://img.shields.io/badge/Version-1.0.0-blue.svg)](https://github.com/yourusername/massapay/releases)

[Download on Google Play](#) | [Privacy Policy](PRIVACY_POLICY_EN.md) | [Report Bug](https://github.com/yourusername/massapay/issues)

</div>

---

## About MassaPay

MassaPay is a **secure, self-custodial cryptocurrency wallet** for the Massa blockchain. Built with privacy and security as core principles, MassaPay gives you complete control over your digital assets.

### Key Features

- **Self-Custodial**: You own your keys, you own your crypto
- **AES-256 Encryption**: Bank-level security for your private keys
- **Biometric Authentication**: Fingerprint and face recognition support
- **No Data Collection**: Zero tracking, zero analytics, complete privacy
- **Open Source**: Fully auditable code
- **Modern UI**: Beautiful Material Design 3 interface with dark/light themes

---

## Why MassaPay?

### Security First
- Private keys encrypted with **AES-256-GCM**
- Secure storage using Android **EncryptedSharedPreferences**
- Optional **biometric authentication** (fingerprint/face)
- **6-digit PIN** protection
- **No central servers** - your data never leaves your device

### Complete Privacy
- **No registration** required (no email, no phone, no KYC)
- **No data collection** or analytics
- **No third-party tracking**
- Open source and auditable

### User-Friendly
- Clean, intuitive interface
- Quick wallet creation (12/24-word seed phrase)
- Easy import from existing wallets
- QR code scanning for addresses
- Real-time price tracking (USD/EUR)
- Transaction history

---

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM + Clean Architecture
- **Dependency Injection**: Hilt
- **Storage**: EncryptedSharedPreferences
- **Networking**: Retrofit + OkHttp
- **Cryptography**: BouncyCastle + Android Keystore
- **Blockchain**: Massa (Ed25519, BLAKE3)

---

## Project Structure

```
massapay/
├── app/              # Main application module
├── core/             # Core models and utilities
├── network/          # Massa API and repositories
├── security/         # Cryptography and key management
├── ui/               # UI components and screens
└── price/            # Price tracking module
```

---

## Building from Source

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34
- Gradle 8.4+

### Clone and Build

```bash
git clone https://github.com/yourusername/massapay.git
cd massapay
./gradlew assembleDebug
```

The APK will be in `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

For signed release builds, you need to create a `keystore.properties` file:

```properties
storePassword=YOUR_KEYSTORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=YOUR_KEY_ALIAS
storeFile=path/to/your/keystore.jks
```

Then build:

```bash
./gradlew bundleRelease
```

---

## Security

### Cryptography

- **Key Derivation**: BIP-39 (12/24-word seed phrases)
- **Encryption**: AES-256-GCM
- **Signing**: Ed25519 (Massa standard)
- **Hashing**: BLAKE3 (Massa standard)
- **Storage**: Android Keystore + EncryptedSharedPreferences

### Best Practices

- All sensitive data encrypted at rest
- No private keys transmitted over network
- Secure random number generation
- Protection against screen recording
- Certificate pinning for API calls

### Responsible Disclosure

If you discover a security vulnerability, please email: security@massapay.online

**Do not** create a public GitHub issue for security vulnerabilities.

---

## Privacy Policy

Read our full privacy policy: [PRIVACY_POLICY_EN.md](PRIVACY_POLICY_EN.md)

**TL;DR**: We don't collect, store, or sell any of your data. Everything stays on your device.

---

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### How to Contribute

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## Roadmap

### Version 1.1 (Planned)
- [ ] Multi-account support
- [ ] NFT support
- [ ] WalletConnect integration
- [ ] Hardware wallet support

### Version 1.2 (Future)
- [ ] DApp browser
- [ ] Staking support
- [ ] Smart contract interaction
- [ ] Multi-language support

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- [Massa](https://massa.net/) - The decentralized blockchain
- [Material Design 3](https://m3.material.io/) - UI design system
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI toolkit
- [BouncyCastle](https://www.bouncycastle.org/) - Cryptography library

---

## Support

- **Email**: support@massapay.online
- **Issues**: [GitHub Issues](https://github.com/yourusername/massapay/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/massapay/discussions)

---

## Disclaimer

MassaPay is a self-custodial wallet. **You are solely responsible for**:

- Backing up your seed phrase
- Keeping your seed phrase secure
- Not sharing your private keys
- Protecting your device

**We cannot recover lost wallets.** If you lose your seed phrase, your funds are **permanently lost**.

Use at your own risk. Cryptocurrency transactions are irreversible.

---

<div align="center">

**Made with ❤️ for the Massa community**

[Website](https://massapay.online) • [Twitter](#) • [Discord](#)

</div>
