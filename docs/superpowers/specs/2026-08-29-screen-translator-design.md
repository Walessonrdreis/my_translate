# Screen Translator (Android) — Design

## Objetivo

App Android que traduz texto em tela para PT-BR sem sair do app onde o usuário está lendo (livro, artigo, etc). Usuário toca em uma bolha flutuante, o app captura a tela, detecta e traduz os textos, e sobrepõe a tradução diretamente em cima do texto original.

Funciona 100% offline, sem custos de API, usando ML Kit on-device.

## Fluxo de uso (v1)

1. Usuário concede permissões (overlay + captura de tela) na primeira execução.
2. Uma bolha flutuante arrastável fica sempre visível sobre outros apps.
3. Usuário toca na bolha.
4. Sistema mostra o diálogo padrão do Android pedindo confirmação de gravação/transmissão de tela (obrigatório pelo MediaProjection, aparece a cada ativação).
5. App captura um screenshot da tela atual.
6. OCR (ML Kit Text Recognition) roda sobre o screenshot e retorna blocos de texto com suas posições (bounding boxes).
7. Cada bloco de texto em inglês é traduzido para PT-BR (ML Kit Translation, on-device).
8. Um overlay é desenhado sobre a tela real: para cada bloco detectado, um retângulo (fundo sólido/blur) na posição exata do bloco original, com o texto traduzido dentro.
9. Usuário lê a tradução sobreposta.
10. Usuário toca em qualquer ponto fora das caixas de tradução → overlay de tradução é removido, tela volta ao normal. A bolha continua visível para nova tradução.

Não há modo de seleção manual de área na v1 — a tradução é sempre da tela inteira visível no momento do toque.

## Arquitetura

### Componentes

**1. `BubbleService` (Foreground Service)**
- Mantém a bolha flutuante via `WindowManager` (TYPE_APPLICATION_OVERLAY).
- Bolha é arrastável (usuário pode reposicionar).
- Ao tocar (tap, não drag) na bolha, dispara o fluxo de captura.
- Roda como Foreground Service com notificação persistente (exigência do Android para MediaProjection + overlay em background).

**2. `ScreenCaptureManager`**
- Encapsula `MediaProjectionManager` / `MediaProjection` / `ImageReader`.
- Solicita permissão de captura (`Activity.startActivityForResult` com o intent do `MediaProjectionManager`) — necessário na primeira ativação da sessão; o token de permissão dura enquanto o processo estiver vivo, então não repete a cada toque na mesma sessão de app, mas repete se o app for encerrado e reaberto.
- Retorna um `Bitmap` do frame atual da tela sob demanda.

**3. `TextRecognitionManager`**
- Usa `com.google.mlkit:text-recognition` (modelo latino, cobre inglês).
- Entrada: `Bitmap` do screenshot.
- Saída: lista de blocos de texto, cada um com `texto original` + `bounding box (Rect)`.

**4. `TranslationManager`**
- Usa `com.google.mlkit:translate`.
- Baixa o modelo EN→PT na primeira tradução (requer Wi-Fi ou confirmação do usuário se estiver em dados móveis — configuração padrão do ML Kit) e o mantém em cache local depois disso.
- Traduz cada bloco de texto individualmente (mantendo o mapeamento bloco→bounding box).

**5. `TranslationOverlayView` (dentro de um segundo overlay `WindowManager`, TYPE_APPLICATION_OVERLAY)**
- Recebe a lista de `(Rect, textoTraduzido)`.
- Desenha, para cada item, um retângulo de fundo (cor sólida clara ou leve blur) posicionado no `Rect` exato, com o texto traduzido renderizado dentro (ajustando tamanho de fonte para caber).
- Um `OnTouchListener` cobrindo toda a view: toque fora de qualquer bounding box remove o overlay inteiro (`WindowManager.removeView`).

**6. `MainActivity`**
- Tela mínima: explica o app, botão para conceder permissão de overlay (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`), botão para iniciar o `BubbleService`.
- Não é usada durante o fluxo de tradução em si (tudo acontece via overlay/serviço).

### Fluxo de dados

```
Toque na bolha
   → ScreenCaptureManager.captureFrame() → Bitmap
   → TextRecognitionManager.recognize(Bitmap) → List<TextBlock(text, boundingBox)>
   → TranslationManager.translateAll(blocks) → List<TextBlock(translatedText, boundingBox)>
   → TranslationOverlayView.show(List<TextBlock>)
```

Tudo roda de forma assíncrona (coroutines), com um pequeno indicador de carregamento na própria bolha (ex.: ícone gira) enquanto o pipeline OCR+tradução processa.

### Permissões necessárias

- `SYSTEM_ALERT_WINDOW` (overlay da bolha e da tradução)
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PROJECTION` (Android 14+ exige tipo específico)
- Permissão de captura de tela via `MediaProjectionManager` (concedida em runtime, não é uma permissão de Manifest declarativa, é um intent de sistema)
- Acesso à internet **apenas** para o download inicial do modelo de tradução ML Kit (`INTERNET` — não usado depois disso)

### Tratamento de erros

- **Nenhum texto detectado**: bolha mostra brevemente um ícone de "nada encontrado" (ex. sombra vermelha por 1s) e não abre overlay.
- **Modelo de tradução ainda não baixado e sem internet**: overlay mostra uma mensagem simples "Baixe o modelo de tradução com internet ativa" em vez de travar.
- **Permissão de overlay não concedida**: app não inicia o serviço da bolha, `MainActivity` mostra estado "permissão necessária" com botão de atalho às configurações.
- **Usuário nega a captura de tela no diálogo do sistema**: bolha simplesmente não faz nada (sem crash), fica pronta para nova tentativa.

### Testes

- Unitário: `TranslationManager` e `TextRecognitionManager` testados com mocks das APIs do ML Kit (verificar mapeamento correto bloco→bounding box, tratamento de lista vazia).
- Manual (não há como automatizar bem overlays de sistema em CI): checklist de fluxo completo em dispositivo real — conceder permissões, tocar bolha, ver tradução sobreposta, tocar fora, repetir.

## Fora de escopo (v1)

- Seleção manual de área (pode virar v2 caso a tradução automática da tela inteira gere ruído demais em telas com muito texto/UI).
- Múltiplos idiomas de origem (v1 fixa EN→PT-BR).
- Histórico de traduções.
- Ajuste de posição/tamanho de fonte configurável pelo usuário.
