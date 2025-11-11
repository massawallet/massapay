# Política de Privacidad de MassaPay

**Última actualización: 10 de noviembre de 2025**

## Introducción

MassaPay ("nosotros", "nuestro" o "la aplicación") respeta tu privacidad y se compromete a proteger tus datos personales. Esta Política de Privacidad describe cómo manejamos tu información al usar nuestra aplicación de billetera de criptomonedas Massa.

## Información que Recopilamos

### Información que NO recopilamos

MassaPay es una billetera autocustodiada (self-custodial). Esto significa que:

- **NO recopilamos** tus claves privadas
- **NO recopilamos** tus frases semilla (seed phrases)
- **NO recopilamos** información personal identificable
- **NO rastreamos** tu actividad
- **NO compartimos** datos con terceros
- **NO vendemos** tu información

### Información almacenada localmente

La siguiente información se almacena **únicamente en tu dispositivo** de forma encriptada:

- **Claves privadas**: Almacenadas usando Android EncryptedSharedPreferences con cifrado AES-256
- **Frases semilla**: Encriptadas localmente, nunca transmitidas
- **Direcciones de billetera**: Generadas localmente
- **Historial de transacciones**: Caché local para mejorar el rendimiento
- **Preferencias de usuario**: Configuraciones de la aplicación (tema, idioma, etc.)
- **PIN de seguridad**: Hasheado y almacenado localmente

## Cómo Usamos Tu Información

Toda la información se procesa **localmente en tu dispositivo** para:

- Generar y administrar tu billetera Massa
- Firmar transacciones de blockchain
- Mostrar tu balance y historial de transacciones
- Proteger tu billetera con PIN y autenticación biométrica
- Personalizar la experiencia de usuario

## Conexiones de Red

MassaPay se conecta a:

### Blockchain de Massa
- **Propósito**: Consultar balances, enviar transacciones, verificar estado de la red
- **Nodos**: Nodos públicos de Massa mainnet
- **Datos transmitidos**: Direcciones públicas de billetera, transacciones firmadas
- **Datos NO transmitidos**: Claves privadas, frases semilla, información personal

### API de Precios (Opcional)
- **Propósito**: Obtener el precio actual de MAS en USD/EUR
- **Proveedor**: APIs públicas de mercado de criptomonedas
- **Datos transmitidos**: Ningún dato personal, solo solicitudes de precio público

## Seguridad de Datos

Implementamos medidas de seguridad de nivel empresarial:

- **Cifrado AES-256-GCM**: Para almacenamiento local de claves
- **Android Keystore**: Protección de claves criptográficas
- **Autenticación biométrica**: Soporte para huella digital y reconocimiento facial
- **PIN de 6 dígitos**: Capa adicional de seguridad
- **Sin servidores centrales**: Tus datos nunca salen de tu dispositivo

## Tus Responsabilidades

Como billetera autocustodiada, **TÚ eres responsable de**:

- Hacer backup de tu frase semilla de forma segura
- Mantener tu PIN seguro y confidencial
- No compartir tus claves privadas con nadie
- Proteger tu dispositivo con contraseña/biometría
- Actualizar la app regularmente

⚠️ **IMPORTANTE**: Si pierdes tu frase semilla o PIN, **NO podemos ayudarte a recuperar tu billetera**. No tenemos acceso a tus claves.

## Permisos de Android

MassaPay solicita los siguientes permisos:

| Permiso | Propósito | Requerido |
|---------|-----------|-----------|
| **INTERNET** | Conectar con blockchain de Massa | Sí |
| **CAMERA** | Escanear códigos QR para direcciones | Opcional |
| **USE_BIOMETRIC** | Autenticación con huella/facial | Opcional |
| **VIBRATE** | Feedback táctil | Opcional |

## Datos de Terceros

MassaPay NO comparte datos con terceros. Las únicas conexiones externas son:

1. **Blockchain de Massa**: Red pública descentralizada
2. **APIs de precio**: Datos públicos de mercado (anónimos)

No usamos servicios de análisis, publicidad o tracking.

## Privacidad de Menores

MassaPay no está diseñado para menores de 18 años. No recopilamos intencionalmente información de menores. Si eres padre/tutor y crees que tu hijo nos ha proporcionado información, contáctanos para eliminarla.

## Transferencias Internacionales

Como no recopilamos ni transmitimos datos personales a servidores, no hay transferencias internacionales de datos.

## Tus Derechos

Tienes derecho a:

- **Acceder** a todos tus datos (almacenados localmente en tu dispositivo)
- **Exportar** tu historial de transacciones
- **Eliminar** todos los datos desinstalando la aplicación
- **Portar** tu billetera usando tu frase semilla en otras apps compatibles

## Cambios a Esta Política

Podemos actualizar esta Política de Privacidad ocasionalmente. Te notificaremos de cambios importantes mediante:

- Actualización de la fecha "Última actualización" en la parte superior
- Notificación en la aplicación (para cambios significativos)

## Cumplimiento Legal

### GDPR (Reglamento General de Protección de Datos - Europa)
MassaPay cumple con GDPR porque:
- No procesa datos personales en servidores
- Todo es local y encriptado
- El usuario tiene control total de sus datos

### CCPA (Ley de Privacidad del Consumidor de California)
MassaPay cumple con CCPA porque:
- No vendemos información personal
- No compartimos datos con terceros
- No recopilamos datos personales identificables

## Código Abierto

MassaPay es software de código abierto. Puedes revisar el código fuente para verificar nuestras prácticas de privacidad:

- **Repositorio**: [GitHub - MassaPay](https://github.com/tuusuario/massapay) *(actualiza con tu URL real)*

## Contacto

Si tienes preguntas sobre esta Política de Privacidad o sobre tus datos, contáctanos:

- **Email**: privacy@massapay.online *(actualiza con tu email real)*
- **Sitio web**: https://massapay.online
- **Ubicación**: Luján, Buenos Aires, Argentina

## Transparencia

MassaPay se compromete a la transparencia total:

✅ **Código abierto**: Puedes auditar nuestro código  
✅ **Sin tracking**: Cero analíticas o rastreadores  
✅ **Sin anuncios**: Nunca mostraremos publicidad  
✅ **Sin venta de datos**: Tus datos son tuyos, no nuestros  
✅ **Autocustodia**: TÚ controlas tus claves, siempre  

---

**MassaPay - Tu billetera Massa, completamente privada.**

*Esta política de privacidad fue generada el 10 de noviembre de 2025 y cumple con las regulaciones de Google Play Store, GDPR, CCPA y mejores prácticas de privacidad para aplicaciones de criptomonedas.*
