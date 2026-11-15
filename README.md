# Sand RGB ESP

Client-side Fabric mod for **Minecraft Java 1.21.11** + Resource Pack de Outline RGB.

## Mod (ESP)
- Detecta areia normal e areia vermelha num raio de 12 blocos.
- Desenha um outline RGB animado em volta dos blocos detectados.
- Pressione **K** para ligar/desligar o ESP.
- Atualiza a lista de blocos a cada 10 ticks para melhor performance.

## Resource Pack (Outline de Seleção)
Na pasta `resourcepack/` está um Resource Pack completo que muda a **linha de seleção** (quando você mira num bloco) para RGB animado.

### Como usar o Resource Pack:
1. Baixe a pasta `resourcepack` (ou faça zip dela).
2. Coloque na pasta `resourcepacks` do Minecraft.
3. Ative o pack no jogo.
4. A linha preta padrão vira um outline RGB animado.

### Configurações do shader (edite o `.fsh`):
- `RAINBOW_SPEED` → velocidade do RGB
- `OUTLINE_ALPHA` → opacidade da linha

## Build do Mod
Requer Java 21 e Gradle.
```bash
gradle build
```
O JAR sai em `build/libs/`.

GitHub Actions faz build automático em todo push.

## Instalação do Mod
Coloque o JAR na pasta `mods` junto com Fabric Loader + Fabric API para 1.21.11.

## Ainda não implementado
- Renderização verdadeira através de paredes (ESP full)
- Setas direcionais / labels de distância
- Detecção de Suspicious Sand
