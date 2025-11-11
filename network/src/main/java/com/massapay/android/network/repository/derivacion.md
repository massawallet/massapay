# 🔐 Bearby Extension - Resumen de Tecnologías Criptográficas

## Descripción General

Este documento detalla todas las tecnologías criptográficas utilizadas en **Bearby Extension** desde la generación de la semilla (seed) hasta la obtención de las claves públicas y privadas.

---

## 📋 Flujo Completo de Generación

```
1. ENTROPÍA → 2. MNEMONIC → 3. SEED → 4. LLAVES HD → 5. ED25519 → 6. DIRECCIÓN
```

---

## 1️⃣ Generación de Entropía

### Tecnología: **Web Crypto API - CSPRNG**

**Ubicación**: `lib/crypto/random.ts`

```typescript
export function randomBytes(length: number): Uint8Array {
  const crypto = globalThis.crypto;
  const a = new Uint8Array(length);
  
  for (let i = 0; i < length; i += QUOTA) {
    crypto.getRandomValues(a.subarray(i, i + Math.min(length - i, QUOTA)));
  }
  
  return a;
}
```

**Características**:
- **Fuente**: Generador de Números Pseudo-Aleatorios Criptográficamente Seguro (CSPRNG)
- **API**: Web Crypto API nativa del navegador
- **Tamaño**: 128-256 bits (dependiendo de 12-24 palabras)
- **Seguridad**: Entropía de calidad criptográfica garantizada por el navegador

---

## 2️⃣ Conversión a Mnemonic (BIP39)

### Tecnología: **BIP39 (Bitcoin Improvement Proposal 39)**

**Ubicación**: `lib/bip39/mnemonic.ts`

```typescript
async generateMnemonic(size = 12) {
  // 1. Genera bytes aleatorios (entropía)
  let entropy = randomBytes((size / 3) * 4);
  
  // 2. Convierte la entropía en palabras mnemónicas
  let words = await this.entropyToMnemonic(entropy, size);
  
  return words; // Array de 12-24 palabras
}
```

**Proceso Detallado**:

1. **Generación de Checksum**:
   - Se aplica SHA-256 a la entropía
   - Se toman los primeros bits del hash como checksum
   
2. **Conversión a Palabras**:
   - Se concatena entropía + checksum
   - Se divide en segmentos de 11 bits
   - Cada segmento se mapea a una palabra de la wordlist BIP39 (2048 palabras)

3. **Validación**:
   - 12 palabras = 128 bits de entropía
   - 24 palabras = 256 bits de entropía

**Tecnologías Utilizadas**:
- **SHA-256**: Para generar checksum
- **BIP39 Wordlist**: Lista estándar de 2048 palabras

---

## 3️⃣ Derivación de Seed (512 bits)

### Tecnología: **PBKDF2-SHA512**

**Ubicación**: `lib/crypto/pbkdf2.ts`

```typescript
export async function pbkdf2(
  password: Uint8Array,
  salt: Uint8Array,
  iterations: number,
  algorithms = ShaAlgorithms.Sha512
) {
  const passphraseKey = await globalThis.crypto.subtle.importKey(
    "raw",
    password,
    { name: "PBKDF2" },
    false,
    ["deriveBits", "deriveKey"]
  );
  
  const webKey = await globalThis.crypto.subtle.deriveKey(
    {
      salt,
      iterations,
      name: "PBKDF2",
      hash: algorithms,
    },
    passphraseKey,
    {
      name: "HMAC",
      hash: algorithms,
      length: 512,
    },
    true,
    ["sign", "verify"]
  );
  
  const hash = await globalThis.crypto.subtle.exportKey("raw", webKey);
  return new Uint8Array(hash);
}
```

**Parámetros**:
- **Input**: Mnemonic phrase (normalizada)
- **Salt**: `"mnemonic" + password_opcional`
- **Iteraciones**: 2048 (configurable)
- **Hash**: SHA-512
- **Output**: 512 bits (64 bytes)

**Características**:
- **Key Stretching**: Las iteraciones hacen costoso los ataques de fuerza bruta
- **Derivación Determinística**: Misma frase + password = misma seed
- **Estándar**: BIP39 specification

---

## 4️⃣ Derivación Jerárquica de Llaves (HD Wallet)

### Tecnología: **BIP32 + HMAC-SHA512**

**Ubicación**: `lib/bip39/hd-key.ts`

### 4.1 Master Key Generation

```typescript
async #fromMasterSeed(seed: Uint8Array) {
  const I = await hmac(ED25519_CURVE, seed);
  
  this.#key = I.slice(0, 32);       // Llave privada maestra (32 bytes)
  this.#chainCode = I.slice(32);     // Chain code (32 bytes)
}
```

**Proceso**:
- **HMAC Key**: `"ed25519 seed"` (constante de curva)
- **HMAC Data**: Seed de 512 bits
- **Output**: 64 bytes divididos en:
  - Llave privada maestra (32 bytes)
  - Chain code (32 bytes)

### 4.2 Child Key Derivation

```typescript
async #deriveChild(index: number) {
  const key = Uint8Array.from(this.#key || []);
  const indexBuffer = writeUint32BE(new Uint8Array(4), index, 0);
  const data = Uint8Array.from([
    ...new Uint8Array(1),    // 0x00 padding
    ...key,                   // Llave privada actual
    ...indexBuffer            // Índice
  ]);
  
  const I = await hmac(Uint8Array.from(this.#chainCode || []), data);
  
  this.#key = I.slice(0, 32);
  this.#chainCode = I.slice(32);
}
```

**Derivation Path**: `m/44'/632'/0'/0'/index'`

- `m`: Master key
- `44'`: BIP44 purpose (Hardened)
- `632'`: Massa coin type (Hardened)
- `0'`: Account (Hardened)
- `0'`: Change (Hardened)
- `index'`: Address index (Hardened)

**Características**:
- **Hardened Derivation**: Todos los índices + 0x80000000
- **Determinístico**: Misma seed + path = mismas llaves
- **Jerárquico**: Se pueden derivar infinitas llaves hijas

---

## 5️⃣ Generación de Par de Llaves Ed25519

### Tecnología: **Ed25519 (Twisted Edwards Curve25519)**

**Ubicación**: `lib/crypto/ed25519.ts`

### Parámetros de la Curva

```typescript
const P = 2n ** 255n - 19n;  // Campo primo
const N = 2n ** 252n + 27742317777372353535851937790883648493n;  // Orden
const Gx = 0x216936d3cd6e53fec0a4e231fdd6dc5c692cc7609525a7b2c9562d608f25d51an;
const Gy = 0x6666666666666666666666666666666666666666666666666666666666666658n;

const CURVE = {
  a: -1n,
  d: 37095705934669439343138083508754565189542113879843219016388785533085940283555n,
  p: P,
  n: N,
  h: 8,        // Cofactor
  Gx: Gx,
  Gy: Gy
};
```

**Ecuación de la Curva**: `−x² + y² = 1 + dx²y²`

### Proceso de Generación de Llave Pública

```typescript
const getPublicKeyAsync = (priv: Hex): Promise<Bytes> =>
  getExtendedPublicKeyAsync(priv).then((p) => p.pointBytes);

const hash2extK = (hashed: Bytes): ExtK => {
  const head = hashed.slice(0, 32);
  
  // Clamping de la llave privada
  head[0] &= 248;   // 0b1111_1000
  head[31] &= 127;  // 0b0111_1111
  head[31] |= 64;   // 0b0100_0000
  
  const prefix = hashed.slice(32, 64);
  const scalar = modL_LE(head);          // Reducción modular
  const point = G.mul(scalar);            // Multiplicación escalar: PubKey = PrivKey × G
  const pointBytes = point.toRawBytes();  // Serialización a bytes
  
  return { head, prefix, scalar, point, pointBytes };
};
```

**Características**:
- **Llave Privada**: 32 bytes (256 bits)
- **Llave Pública**: 32 bytes (punto de curva comprimido)
- **Clamping**: Asegura que la llave esté en el rango correcto
- **Librería**: **noble-ed25519** (implementación JavaScript pura)
- **Performance**: Optimizaciones con wNAF y precomputación

### Operaciones de Curva

```typescript
class Point {
  // Suma de puntos (Complete formula)
  add(other: Point): Point { ... }
  
  // Duplicación de puntos
  double(): Point { ... }
  
  // Multiplicación escalar (double-and-add)
  mul(n: bigint, safe = true): Point { ... }
  
  // Conversión a coordenadas afines
  toAffine(): AffinePoint { ... }
}
```

---

## 6️⃣ Generación de Dirección

### Tecnología: **BLAKE3 + Base58Check**

**Ubicación**: `lib/address/index.ts`

```typescript
export async function addressFromPublicKey(publicKey: PublicKey) {
  // 1. Codifica la versión con Varint
  const version = new VarintEncode().encode(publicKey.version);
  
  // 2. Hash de la llave pública con BLAKE3
  const pubKeyHash = utils.hex.toBytes(
    blake3.newRegular().update(publicKey.pubKey).finalize()
  );
  
  // 3. Codifica con Base58Check
  const encoded = await base58Encode(
    Uint8Array.from([...version, ...pubKeyHash])
  );
  
  // 4. Añade prefijo específico de Massa
  return ADDRESS_PREFIX + encoded;  // "AU" + base58
}
```

### Base58Check Encoding

```typescript
async function encode(data: Uint8Array, prefix = "00") {
  const bufPrefix = utils.hex.toBytes(prefix);
  let hash = new Uint8Array([...bufPrefix, ...data]);

  // Doble SHA-256 para checksum
  hash = await sha256(hash);
  hash = await sha256(hash);
  
  // Concatena: prefix + data + checksum(4 bytes)
  hash = new Uint8Array([...bufPrefix, ...data, ...hash.slice(0, 4)]);

  return binaryToBase58(hash);
}
```

**Proceso Completo**:
1. **Varint Encoding**: Codifica versión del protocolo
2. **BLAKE3 Hash**: Hash de 32 bytes de la llave pública
3. **Double SHA-256**: Genera checksum de 4 bytes
4. **Base58 Encoding**: Convierte a formato legible
5. **Prefijos**:
   - `"AU"`: Direcciones de usuario
   - `"AS"`: Direcciones de contratos

**Tecnologías**:
- **BLAKE3**: Función hash moderna (más rápida que SHA-256)
- **SHA-256**: Para checksum (compatibilidad)
- **Base58**: Alfabeto sin caracteres ambiguos (sin 0, O, I, l)
- **Varint**: Codificación de enteros de longitud variable

---

## 7️⃣ Encriptación y Almacenamiento

### Tecnología: **AES-256-CTR**

**Ubicación**: `lib/crypto/aes.ts`

```typescript
export const Cipher = Object.freeze({
  encrypt(content: Uint8Array, key: Uint8Array) {
    const entropy = randomBytes(16);         // IV aleatorio
    const iv = new Counter(entropy);
    const aesCtr = new ModeOfOperation.ctr(key, iv);
    const encrypted = aesCtr.encrypt(content);
    
    // Formato: "encrypted_hex/iv_hex"
    const bytes = utils.utf8.toBytes(
      `${utils.hex.fromBytes(encrypted)}/${utils.hex.fromBytes(entropy)}`
    );
    
    return bytes;
  },
  
  decrypt(bytes: Uint8Array, key: Uint8Array) {
    const [encrypted, iv] = utils.utf8.fromBytes(bytes).split("/");
    
    const counter = new Counter(utils.hex.toBytes(iv));
    const aesCtr = new ModeOfOperation.ctr(key, counter);
    
    return aesCtr.decrypt(utils.hex.toBytes(encrypted));
  }
});
```

**Características**:
- **Algoritmo**: AES-256 en modo CTR (Counter)
- **IV**: 16 bytes aleatorios por cada encriptación
- **Llave**: Derivada del password del usuario con PBKDF2
- **Formato de salida**: `encrypted_hex/iv_hex`
- **Librería**: **aes-js**

**Seguridad**:
- IV único para cada encriptación (evita reutilización)
- Modo CTR permite paralelización
- Llave de 256 bits (máxima seguridad AES)

---

## 📊 Tabla Resumen de Tecnologías

| Etapa | Tecnología | Biblioteca/API | Input | Output |
|-------|-----------|----------------|-------|--------|
| **1. Entropía** | CSPRNG | Web Crypto API | - | 128-256 bits |
| **2. Checksum** | SHA-256 | Web Crypto API | Entropía | 256 bits → primeros bits |
| **3. Mnemonic** | BIP39 | Implementación propia | Entropía + Checksum | 12-24 palabras |
| **4. Seed** | PBKDF2-SHA512 | Web Crypto API | Mnemonic + Password | 512 bits (64 bytes) |
| **5. Master Key** | HMAC-SHA512 | Web Crypto API | Seed | 64 bytes (key + chain) |
| **6. Child Keys** | HMAC-SHA512 + BIP32 | Web Crypto API | Parent key + Index | 64 bytes por nivel |
| **7. Priv → Pub** | Ed25519 | noble-ed25519 | Private key (32B) | Public key (32B) |
| **8. Pub → Hash** | BLAKE3 | blake3-js | Public key | 32 bytes |
| **9. Checksum** | SHA-256 (doble) | Web Crypto API | Prefix + Hash | 4 bytes |
| **10. Dirección** | Base58Check | Implementación propia | Version + Hash + Check | String (AU...) |
| **11. Encriptación** | AES-256-CTR | aes-js | Private key + Password | Encrypted bytes |

---

## 🔑 Estándares Implementados

### BIP39 - Mnemonic Code
- **Propósito**: Generar frases mnemónicas legibles para humanos
- **Wordlist**: 2048 palabras en inglés (estándar)
- **Checksum**: Validación de integridad
- **Normalización**: NFKD Unicode normalization

### BIP32 - Hierarchical Deterministic Wallets
- **Propósito**: Derivación jerárquica de llaves
- **Master seed**: Derivada de mnemonic
- **Child derivation**: HMAC-SHA512 based
- **Chain code**: Añade entropía adicional

### BIP44 - Multi-Account Hierarchy
- **Path**: `m / purpose' / coin_type' / account' / change' / address_index'`
- **Massa path**: `m/44'/632'/0'/0'/index'`
- **Hardened**: Todos los niveles usan derivación hardened
- **Coin type**: 632 para Massa blockchain

### Ed25519 - EdDSA Signature Scheme
- **Curva**: Twisted Edwards Curve25519
- **Firma**: EdDSA (Edwards-curve Digital Signature Algorithm)
- **Longitud**: 64 bytes por firma
- **Performance**: ~10x más rápido que ECDSA

### Base58Check - Address Encoding
- **Alfabeto**: Excluye 0, O, I, l (anti-confusión)
- **Checksum**: 4 bytes de doble SHA-256
- **Prefijos**: Identificación de tipo de dirección

---

## 💻 Librerías y APIs Utilizadas

### 1. **Web Crypto API** (Nativa del navegador)
```typescript
globalThis.crypto.getRandomValues()
globalThis.crypto.subtle.importKey()
globalThis.crypto.subtle.deriveKey()
globalThis.crypto.subtle.sign()
```

**Funciones**:
- Generación de números aleatorios criptográficos
- PBKDF2 key derivation
- HMAC-SHA512 operations
- SHA-256 hashing

**Ventajas**:
- ✅ Nativa del navegador (sin dependencias)
- ✅ Hardware-accelerated cuando es posible
- ✅ Estándar W3C
- ✅ Auditada y mantenida por los fabricantes de navegadores

### 2. **noble-ed25519** (Librería JavaScript)
```typescript
import { getPublicKeyAsync, signAsync, verifyAsync } from 'lib/crypto/ed25519';
```

**Características**:
- Implementación pura en JavaScript/TypeScript
- Sin dependencias de C/C++
- Optimizaciones con wNAF (windowed Non-Adjacent Form)
- Precomputación para operaciones frecuentes
- Verificación ZIP215 y RFC8032 compliant

**Ventajas**:
- ✅ Auditable (código JavaScript legible)
- ✅ Compatible con todos los navegadores
- ✅ Alto rendimiento
- ✅ Mantenida activamente

### 3. **blake3-js** (BLAKE3 Hash)
```typescript
import blake3 from 'blake3-js';
const hash = blake3.newRegular().update(data).finalize();
```

**Características**:
- Implementación de BLAKE3 en JavaScript
- Más rápido que SHA-256 (hasta 10x en algunos casos)
- Salida de 32 bytes (256 bits)
- Usado específicamente por Massa blockchain

**Ventajas**:
- ✅ Seguridad criptográfica moderna
- ✅ Alto rendimiento
- ✅ Paralelizable (cuando es soportado)

### 4. **aes-js** (AES Encryption)
```typescript
import { Counter, ModeOfOperation, utils } from 'aes-js';
```

**Características**:
- AES-256 en modo CTR (Counter)
- Implementación pura en JavaScript
- Soporta todos los modos de operación AES

**Ventajas**:
- ✅ Sin dependencias nativas
- ✅ Compatible con todos los entornos
- ✅ Fácil de auditar

---

## 🔒 Características de Seguridad

### Generación de Entropía
- ✅ **CSPRNG**: Generador criptográficamente seguro del sistema operativo
- ✅ **Calidad**: Entropía de alta calidad garantizada por Web Crypto API
- ✅ **No determinístico**: Cada generación es única

### Key Derivation
- ✅ **PBKDF2**: Key stretching con 2048+ iteraciones
- ✅ **Salt**: Único por wallet ("mnemonic" + password)
- ✅ **SHA-512**: Hash function resistente a colisiones

### Derivación HD
- ✅ **Hardened paths**: Todos los niveles hardened (más seguro)
- ✅ **Chain code**: Entropía adicional en cada derivación
- ✅ **No exposición**: Llave maestra nunca se expone

### Ed25519
- ✅ **Clamping**: Llave privada ajustada al rango seguro
- ✅ **Curva segura**: Sin side-channel attacks conocidos
- ✅ **Firma determinística**: RFC8032 compliant

### Almacenamiento
- ✅ **Encriptación**: AES-256 para llaves privadas importadas
- ✅ **IV único**: Nuevo IV por cada encriptación
- ✅ **No plaintext**: Llaves nunca en texto plano en storage

### Validación
- ✅ **Checksums**: Validación en mnemonic y direcciones
- ✅ **Base58Check**: Detección de errores de tipeo
- ✅ **Varint**: Codificación eficiente con validación

---

## 🎯 Tipos de Cuentas Soportadas

### 1. Seed Account (HD Wallet)
```typescript
async addAccountFromSeed(seed: Uint8Array, name: string) {
  const index = this.lastIndexSeed;
  const pubKey = await this.fromSeed(seed, index);
  const base58 = await addressFromPublicKey(pubKey);
  
  // Tipo: AccountTypes.Seed
  // Llave privada: Derivada on-demand desde seed encriptada
}
```

**Características**:
- Derivadas desde el mnemonic
- Infinitas cuentas posibles
- Llave privada no almacenada directamente
- Respaldadas automáticamente con el mnemonic

### 2. Private Key Account (Importada)
```typescript
async addAccountFromPrivateKey(privateKey: string, name: string) {
  const { pubKey, base58, privKey } = await this.fromPrivateKey(privateKey);
  const encryptedPrivateKey = this.#guard.encryptPrivateKey(privKey);
  
  // Tipo: AccountTypes.PrivateKey
  // Llave privada: Encriptada y almacenada
}
```

**Características**:
- Importadas desde llave privada externa
- Llave privada encriptada con AES-256
- No respaldadas por mnemonic
- Requieren backup individual

### 3. Track Account (Solo Lectura)
```typescript
async addAccountForTrack(base58: string, name: string) {
  // Tipo: AccountTypes.Track
  // Solo observación, no puede firmar
}
```

**Características**:
- Solo dirección pública
- No puede firmar transacciones
- Útil para monitoreo
- Sin llaves almacenadas

---

## 📁 Estructura de Archivos Clave

```
bearby-extension/
├── lib/
│   ├── bip39/
│   │   ├── mnemonic.ts       # Generación y validación de mnemonic
│   │   ├── hd-key.ts         # Derivación HD (BIP32)
│   │   └── wordlists.ts      # Wordlist BIP39
│   ├── crypto/
│   │   ├── random.ts         # CSPRNG wrapper
│   │   ├── pbkdf2.ts         # PBKDF2 implementation
│   │   ├── hmac.ts           # HMAC-SHA512
│   │   ├── sha256.ts         # SHA-256
│   │   ├── sha512.ts         # SHA-512
│   │   ├── ed25519.ts        # Ed25519 (noble-ed25519)
│   │   ├── aes.ts            # AES-256-CTR encryption
│   │   └── base58.ts         # Base58 encoding/decoding
│   ├── address/
│   │   └── index.ts          # Generación de direcciones
│   └── varint/
│       └── index.ts          # Variable-length integer encoding
├── core/background/
│   ├── account/
│   │   └── account.ts        # Account management
│   └── guard/
│       └── guard.ts          # Encryption and vault management
└── types/
    └── account.d.ts          # TypeScript definitions
```

---

## 🔄 Flujo de Creación de Wallet (Diagrama)

```
┌─────────────────────────────────────────────────────────────────┐
│                    CREACIÓN DE NUEVA WALLET                     │
└─────────────────────────────────────────────────────────────────┘

1. Usuario solicita crear wallet
   │
   ├─> Genera entropía (128-256 bits)
   │   └─> crypto.getRandomValues()
   │
2. Convierte a mnemonic (12-24 palabras)
   │   ├─> Calcula checksum (SHA-256)
   │   └─> Mapea bits → palabras BIP39
   │
3. Usuario ingresa password (opcional)
   │
4. Deriva seed de 512 bits
   │   └─> PBKDF2(mnemonic, "mnemonic"+password, 2048, SHA-512)
   │
5. Encripta y almacena seed
   │   └─> AES-256-CTR(seed, password_key)
   │
6. Genera Master Key
   │   └─> HMAC-SHA512("ed25519 seed", seed)
   │   └─> Split: [master_key(32B) | chain_code(32B)]
   │
7. Deriva primera cuenta (path: m/44'/632'/0'/0'/0')
   │   ├─> Child derivation con HMAC-SHA512
   │   ├─> Clamp private key (Ed25519)
   │   └─> Calcula public key: G × private_key
   │
8. Genera dirección
   │   ├─> Hash public key con BLAKE3
   │   ├─> Calcula checksum (SHA-256 × 2)
   │   └─> Codifica con Base58Check
   │   └─> Añade prefijo "AU"
   │
9. Guarda cuenta en Browser Storage
   │
✓ Wallet creada y lista para usar
```

---

## 🔐 Flujo de Firma de Transacción

```
┌─────────────────────────────────────────────────────────────────┐
│                    FIRMA DE TRANSACCIÓN                         │
└─────────────────────────────────────────────────────────────────┘

1. Usuario solicita firmar transacción
   │
2. Valida password y desbloquea vault
   │   └─> Desencripta seed con AES-256-CTR
   │
3. Deriva llave privada para cuenta actual
   │   ├─> Genera master key desde seed
   │   └─> Deriva child key según path (m/44'/632'/0'/0'/index')
   │
4. Prepara mensaje de transacción
   │   └─> Serializa datos de transacción
   │
5. Firma con Ed25519
   │   ├─> Genera nonce: r = SHA-512(prefix || message)
   │   ├─> Calcula R = r × G
   │   ├─> Calcula k = SHA-512(R || A || message)
   │   └─> Calcula S = (r + k × private_key) mod N
   │   └─> Firma = R || S (64 bytes)
   │
6. Broadcast de transacción firmada
   │
✓ Transacción firmada y enviada
```

---

## 📚 Referencias y Recursos

### Especificaciones
- [BIP39 - Mnemonic code for generating deterministic keys](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki)
- [BIP32 - Hierarchical Deterministic Wallets](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki)
- [BIP44 - Multi-Account Hierarchy for Deterministic Wallets](https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki)
- [RFC8032 - Edwards-Curve Digital Signature Algorithm (EdDSA)](https://datatracker.ietf.org/doc/html/rfc8032)
- [FIPS 186-5 - Digital Signature Standard (DSS)](https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.186-5.pdf)
- [BLAKE3 Specification](https://github.com/BLAKE3-team/BLAKE3-specs)

### Librerías
- [Web Crypto API - MDN](https://developer.mozilla.org/en-US/docs/Web/API/Web_Crypto_API)
- [noble-ed25519](https://github.com/paulmillr/noble-ed25519)
- [blake3-js](https://github.com/connor4312/blake3)
- [aes-js](https://github.com/ricmoo/aes-js)

### Massa Blockchain
- [Massa Documentation](https://docs.massa.net/)
- [Massa GitHub](https://github.com/massalabs/massa)

---

## ⚠️ Notas de Seguridad

### Para Desarrolladores

1. **Nunca expongas las llaves privadas**
   - No las logues
   - No las envíes por red sin encriptar
   - No las almacenes en texto plano

2. **Valida todas las entradas**
   - Verifica checksums en mnemonics
   - Valida formatos de direcciones
   - Sanitiza inputs de usuario

3. **Usa entropía de calidad**
   - Siempre usa Web Crypto API para random
   - Nunca uses Math.random() para criptografía
   - Verifica que el navegador soporte crypto

4. **Manejo seguro de memoria**
   - Limpia buffers sensibles después de uso
   - Usa Uint8Array para datos binarios
   - Evita conversiones innecesarias

### Para Usuarios

1. **Respalda tu mnemonic**
   - Escríbelo en papel
   - Guárdalo en un lugar seguro
   - Nunca lo compartas
   - No lo almacenes digitalmente

2. **Password fuerte**
   - Usa password largo y único
   - No reutilices passwords
   - Considera un gestor de contraseñas

3. **Verifica direcciones**
   - Siempre verifica la dirección completa
   - Usa múltiples canales para confirmar
   - Ten cuidado con copiar/pegar

---

## 📝 Changelog de Seguridad

### Versión Actual
- ✅ BIP39 compliant
- ✅ BIP32/BIP44 HD wallets
- ✅ Ed25519 signatures (RFC8032)
- ✅ BLAKE3 hashing
- ✅ AES-256-CTR encryption
- ✅ PBKDF2 key derivation (2048+ iterations)
- ✅ Hardened derivation paths
- ✅ Base58Check encoding

### Mejoras Futuras Consideradas
- [ ] Hardware wallet support (Ledger, Trezor)
- [ ] Multi-signature accounts
- [ ] Social recovery
- [ ] Shamir's Secret Sharing
- [ ] PBKDF2 adaptive iterations

---

## 🤝 Contribuciones

Para contribuir mejoras de seguridad:

1. **Reporta vulnerabilidades** de forma responsable
2. **Propón mejoras** con documentación técnica
3. **Audita el código** y comparte hallazgos
4. **Mejora la documentación** de seguridad

---

## 📜 Licencia

Este documento describe la implementación criptográfica de **Bearby Extension**, un proyecto open-source para Massa blockchain.

**Fecha de creación**: Noviembre 9, 2025

---

**Nota**: Este documento es solo para fines informativos y educativos. Para la implementación más reciente, siempre consulta el código fuente en el repositorio oficial.
