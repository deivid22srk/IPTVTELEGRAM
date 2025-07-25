# Correção para Erro de Memória com Combos Grandes

## Problema Identificado

O erro estava ocorrendo quando o usuário selecionava arquivos de combo muito grandes, causando:
- `OutOfMemoryError` durante o carregamento
- `ArrayList.grow()` falhando por falta de memória
- Travamento da aplicação

**Stack trace do erro:**
```
at java.util.Arrays.copyOf(Arrays.java:3560)
at java.util.ArrayList.grow(ArrayList.java:244)
at com.Scanner.IPTV.MainActivity.loadCombosFromFile(MainActivity.java:213)
```

## Soluções Implementadas

### 1. **Carregamento em Lotes (Batch Loading)**
- Processa arquivos em lotes de 1.000 combos por vez
- Reduz o pico de uso de memória
- Permite progress feedback ao usuário

### 2. **Limites de Memória**
- **Combos:** Máximo de 50.000 combos por arquivo
- **Proxies:** Máximo de 10.000 proxies por arquivo
- Verificação de memória disponível antes do carregamento

### 3. **Gerenciamento de Memória**
- Verificação automática de memória disponível
- Garbage collection forçado após operações pesadas
- Limpeza automática de hits antigos (mantém apenas os últimos 500)

### 4. **Interface Melhorada**
- Progress dialog com contador de combos carregados
- Mensagens informativas sobre limites atingidos
- Avisos quando a memória está baixa

### 5. **Tratamento de Erros Robusto**
- Captura `OutOfMemoryError` e salva o que foi carregado
- Mensagens de erro mais informativas
- Recuperação graceful em caso de problemas

## Principais Mudanças no Código

### LoadCombosTask
```java
// Antes
while ((line = reader.readLine()) != null) {
    if (line.contains(":")) {
        combos.add(line.trim()); // Podia causar OutOfMemoryError
    }
}

// Depois
while ((line = reader.readLine()) != null && count < MAX_COMBOS_DEFAULT) {
    line = line.trim();
    if (!line.isEmpty() && line.contains(":")) {
        combos.add(line);
        count++;
        batchCount++;
        
        // Processa em lotes e atualiza progresso
        if (batchCount >= BATCH_SIZE_DEFAULT) {
            publishProgress(count);
            batchCount = 0;
            Thread.sleep(10); // Libera CPU
        }
    }
}
```

### Verificação de Memória
```java
private boolean isMemoryAvailable() {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    long availableMemory = maxMemory - usedMemory;
    
    return availableMemory > (50 * 1024 * 1024); // 50MB mínimo
}
```

### Limpeza Automática de Recursos
```java
private void freeUnusedResources() {
    // Remove hits antigos se houver muitos (>1000)
    if (hits.size() > 1000) {
        int toRemove = hits.size() - 500;
        for (int i = 0; i < toRemove; i++) {
            hits.remove(0);
            if (hitsContainer.getChildCount() > 0) {
                hitsContainer.removeViewAt(0);
            }
        }
    }
    forceGarbageCollection();
}
```

## Benefícios

1. **Estabilidade:** Elimina crashes por falta de memória
2. **Performance:** Carregamento mais eficiente em lotes
3. **Usabilidade:** Progress feedback e mensagens informativas
4. **Escalabilidade:** Suporta arquivos maiores com controle de recursos
5. **Robustez:** Recuperação graceful em cenários de erro

## Recomendações de Uso

1. **Para arquivos pequenos (<10MB):** Funcionamento normal
2. **Para arquivos médios (10-50MB):** Carregamento em lotes com progress
3. **Para arquivos grandes (>50MB):** Limite automático + aviso ao usuário
4. **Para arquivos muito grandes (>100MB):** Recomenda-se dividir o arquivo

## Testes Recomendados

1. Teste com arquivo pequeno (< 1.000 combos)
2. Teste com arquivo médio (10.000 - 20.000 combos)
3. Teste com arquivo grande (50.000+ combos)
4. Teste em dispositivos com pouca RAM
5. Teste interrompendo o carregamento

## Monitoramento

O app agora exibe:
- Progresso do carregamento em tempo real
- Avisos quando limites são atingidos
- Alertas de memória insuficiente
- Contagem precisa de combos/proxies carregados