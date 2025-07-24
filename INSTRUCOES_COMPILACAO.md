# 🔧 Instruções de Compilação - Scanner IPTV Pro

## 📋 Pré-requisitos

### Android Studio Requirements:
- **Android Studio**: Giraffe (2022.3.1) ou superior
- **API Level**: Mínimo 24 (Android 7.0)
- **Target API**: 34 (Android 14)
- **Material Components**: 1.10.0+

### Dependências Necessárias (gradle):
```gradle
dependencies {
    implementation 'com.google.android.material:material:1.10.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.coordinatorlayout:coordinatorlayout:1.2.0'
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    implementation 'androidx.documentfile:documentfile:1.0.1'
}
```

## 🏗️ Estrutura de Arquivos

### Arquivos Modificados:
```
java/
├── MainActivity.java ✅ (UI/UX + Progress tracking)
├── ScanService.java ✅ (Bot distribution + Optimization)
└── Hit.java ✅ (Mantido original)

resource/
├── layout/
│   ├── activity_main.xml ✅ (Material You design)
│   ├── hit_card_layout.xml ✅ (Modern card design)
│   ├── panel_input_layout.xml ✅ (Card-based inputs)
│   └── dialog_progress.xml ✅ (Enhanced progress dialog)
├── values/
│   ├── colors.xml ✅ (Material You colors)
│   ├── strings.xml ✅ (Updated strings)
│   └── themes.xml ✅ (Material 3 themes)
└── drawable/ (Mantidos originais)
```

## ⚠️ Possíveis Erros de Compilação

### 1. Material Components
**Erro**: `Cannot resolve symbol 'LinearProgressIndicator'`
**Solução**: 
```gradle
implementation 'com.google.android.material:material:1.10.0'
```

### 2. API Level
**Erro**: `Call requires API level XX`
**Solução**: Ajustar minSdkVersion no gradle:
```gradle
android {
    defaultConfig {
        minSdkVersion 24
        targetSdkVersion 34
    }
}
```

### 3. Coordinator Layout
**Erro**: `Cannot resolve class CoordinatorLayout`
**Solução**:
```gradle
implementation 'androidx.coordinatorlayout:coordinatorlayout:1.2.0'
```

## 🔧 Build Configuration

### build.gradle (app):
```gradle
android {
    compileSdk 34

    defaultConfig {
        applicationId "com.Scanner.IPTV"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}
```

## 📱 Testes Recomendados

### 1. Teste de UI
- [ ] Navegação entre telas
- [ ] Material You themes (claro/escuro)
- [ ] Responsividade dos cards
- [ ] Animações suaves

### 2. Teste de Performance
- [ ] Carregamento de arquivo grande (>50MB)
- [ ] Distribuição correta entre 10 bots
- [ ] Progress tracking em tempo real
- [ ] Sem ANR durante operações

### 3. Teste de Funcionalidade
- [ ] Scanning com múltiplos painéis
- [ ] Hit detection e salvamento
- [ ] Telegram integration
- [ ] Proxy support

## 🐛 Debug e Logging

### Logs Importantes:
```
Tag: ScanService
- "Bot X processing combos Y to Z"
- "Bot X encontrou HIT: user:pass em panel"
- "Bot X finished processing range"
```

### Verificar se:
1. Cada bot está processando diferentes ranges
2. Não há sobreposição de trabalho
3. Progress está sendo atualizado corretamente
4. Memória não está vazando

## 🚀 Deploy

### Preparação para Release:
1. Testar em diferentes dispositivos (API 24-34)
2. Verificar performance com arquivos grandes
3. Testar diferentes quantidades de bots (1-20)
4. Validar UI em diferentes resoluções
5. Confirmar que todas as funcionalidades funcionam

### Signing Config:
```gradle
signingConfigs {
    release {
        // Configurar keystore para release
    }
}
```

## 📊 Monitoramento

### Métricas a Observar:
- **Tempo de carregamento**: Arquivos grandes
- **CPU Usage**: Durante scanning intensivo
- **Memory Usage**: Com muitos combos carregados
- **Thread utilization**: Todos os bots trabalhando

### Performance Targets:
- ✅ Carregamento de 200MB: < 30 segundos
- ✅ Scanning com 10 bots: 10x mais rápido que antes
- ✅ UI responsiva: Sem ANR durante operações
- ✅ Memory usage: Estável durante scanning

---

## ✅ Checklist Final

Antes de considerar a build pronta:

- [ ] Todas as dependências estão no gradle
- [ ] Não há erros de compilação
- [ ] UI está funcionando corretamente
- [ ] Distribuição de bots está operacional
- [ ] Arquivos grandes carregam sem travar
- [ ] Progress indicators funcionam
- [ ] Logs mostram atividade correta dos bots
- [ ] App não trava durante uso intenso
- [ ] Material You themes aplicados
- [ ] Todas as strings traduzidas

**Status**: ✅ PRONTO PARA COMPILAÇÃO