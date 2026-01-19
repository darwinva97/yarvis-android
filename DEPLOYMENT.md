# Guía de Despliegue - Yarvis

## 1. Android App

### Generar APK de Debug (para pruebas)

```bash
cd /path/to/yarvis
./gradlew assembleDebug
```

APK generado en: `app/build/outputs/apk/debug/app-debug.apk`

### Generar APK/AAB de Release

#### Paso 1: Crear Keystore (solo una vez)

```bash
keytool -genkey -v \
  -keystore yarvis-release.keystore \
  -alias yarvis \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

**IMPORTANTE:** Guarda el keystore y las contraseñas de forma segura. Si los pierdes, no podrás actualizar la app en Play Store.

#### Paso 2: Configurar variables locales

Crear `~/.gradle/gradle.properties` (o `gradle.properties` en el proyecto):

```properties
RELEASE_STORE_FILE=/path/to/yarvis-release.keystore
RELEASE_STORE_PASSWORD=tu_contraseña
RELEASE_KEY_ALIAS=yarvis
RELEASE_KEY_PASSWORD=tu_contraseña
```

#### Paso 3: Generar Release

```bash
# APK (para distribución directa / tu web)
./gradlew assembleRelease

# AAB (para Play Store)
./gradlew bundleRelease
```

Archivos generados:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

### GitHub Actions (CI/CD)

El workflow `.github/workflows/android-release.yml` genera releases automáticamente.

#### Configurar Secrets en GitHub

1. Ve a tu repo → Settings → Secrets and variables → Actions
2. Agrega estos secrets:

| Secret | Descripción |
|--------|-------------|
| `KEYSTORE_BASE64` | Keystore en base64: `base64 -w 0 yarvis-release.keystore` |
| `KEYSTORE_PASSWORD` | Contraseña del keystore |
| `KEY_ALIAS` | Alias de la key (ej: `yarvis`) |
| `KEY_PASSWORD` | Contraseña de la key |

#### Crear Release

```bash
# Crear tag y push
git tag v1.0.0
git push origin v1.0.0
```

El workflow generará automáticamente:
- APK firmado
- AAB para Play Store
- GitHub Release con los archivos

---

## 2. Backend

### Desarrollo Local

```bash
cd backend

# Instalar dependencias
pnpm install

# Modo desarrollo (con n8n real)
pnpm dev

# Modo mock (sin n8n)
pnpm dev:mock
```

### Docker Local

```bash
cd backend

# Build
docker build -t yarvis-backend .

# Run
docker run -p 3000:3000 \
  -e MOCK_MODE=true \
  yarvis-backend
```

### GitHub Actions (CI/CD)

El workflow `.github/workflows/backend-release.yml` construye y publica la imagen Docker.

#### Crear Release del Backend

```bash
git tag backend-v1.2.0
git push origin backend-v1.2.0
```

La imagen se publica en: `ghcr.io/darwinva97/yarvis-android/yarvis-backend:1.2.0`

---

## 3. Kubernetes / k3s con ArgoCD

### Estructura de archivos

```
k8s/
├── namespace.yaml      # Namespace 'yarvis'
├── configmap.yaml      # Configuración (no sensible)
├── secret.yaml         # Secretos (URLs, tokens)
├── deployment.yaml     # Deployment del backend
├── service.yaml        # Service interno
├── ingress.yaml        # Ingress para acceso externo
├── kustomization.yaml  # Kustomize config
└── argocd-application.yaml  # ArgoCD Application
```

### Preparación para GitOps

#### 1. Actualizar configuración

Editar los siguientes archivos:

**`k8s/deployment.yaml`:**
```yaml
image: ghcr.io/darwinva97/yarvis/yarvis-backend:latest
```

**`k8s/secret.yaml`:**
```yaml
WORKFLOW_WEBHOOK_URL: "http://tu-n8n-service:5678/webhook/yarvis"
```

**`k8s/ingress.yaml`:**
```yaml
host: yarvis.tu-dominio.com
```

**`k8s/argocd-application.yaml`:**
```yaml
repoURL: https://github.com/darwinva97/yarvis.git
```

#### 2. Configurar acceso al registro de GitHub (si el repo es privado)

```bash
# Crear secret para pull de imágenes
kubectl create secret docker-registry ghcr-secret \
  --namespace yarvis \
  --docker-server=ghcr.io \
  --docker-username=darwinva97 \
  --docker-password=TU_GITHUB_TOKEN
```

Descomentar en `deployment.yaml`:
```yaml
imagePullSecrets:
  - name: ghcr-secret
```

#### 3. Desplegar con ArgoCD

**Opción A: Aplicar manualmente la Application**

```bash
kubectl apply -f k8s/argocd-application.yaml
```

**Opción B: Desde la UI de ArgoCD**

1. Ir a ArgoCD UI → New App
2. Configurar:
   - Application Name: `yarvis-backend`
   - Project: `default`
   - Repository URL: `https://github.com/darwinva97/yarvis.git`
   - Path: `k8s`
   - Cluster: tu cluster
   - Namespace: `yarvis`
3. Habilitar Auto-Sync

#### 4. Verificar despliegue

```bash
# Ver estado en ArgoCD
argocd app get yarvis-backend

# Ver pods
kubectl get pods -n yarvis

# Ver logs
kubectl logs -n yarvis -l app.kubernetes.io/name=yarvis -f

# Test del health endpoint
kubectl port-forward -n yarvis svc/yarvis-backend 3000:80
curl http://localhost:3000/health
```

### Overlays para múltiples ambientes (opcional)

Para diferentes configuraciones (dev/staging/prod), crear overlays:

```
k8s/
├── base/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── kustomization.yaml
└── overlays/
    ├── dev/
    │   ├── kustomization.yaml
    │   └── configmap-patch.yaml
    └── prod/
        ├── kustomization.yaml
        └── configmap-patch.yaml
```

---

## 4. Configuración del Android App para Producción

Antes de publicar, actualiza la URL del backend en:

**`app/src/main/java/com/yarvis/assistant/network/ServerConfig.java`:**

```java
private static final String DEFAULT_SERVER_URL = "wss://yarvis.tu-dominio.com/ws";
```

Nota: Usa `wss://` (WebSocket Secure) en producción.

---

## Checklist de Release

### Android
- [ ] Actualizar `versionCode` y `versionName` en `app/build.gradle`
- [ ] Actualizar URL del backend para producción
- [ ] Probar en dispositivo real
- [ ] Generar APK/AAB firmado
- [ ] Subir a Play Store / tu web

### Backend
- [ ] Actualizar versión en `package.json`
- [ ] Probar con Docker localmente
- [ ] Crear tag `backend-vX.X.X`
- [ ] Verificar imagen en ghcr.io
- [ ] Verificar sync en ArgoCD

### Infraestructura
- [ ] Configurar dominio DNS
- [ ] Configurar certificado TLS
- [ ] Verificar conectividad con n8n
- [ ] Probar WebSocket desde Android
