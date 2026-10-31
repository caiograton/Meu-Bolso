package com.caiograton.meubolso;

/**
 * Decide qual altura de teclado o app deve usar a cada momento.
 * O Android informa a altura FINAL do teclado assim que ele começa a abrir ou
 * fechar; se o app usasse esse valor direto, o layout pularia para a posição
 * final enquanto o teclado ainda está deslizando (o "cinza" mais rápido que o
 * teclado). Durante a animação usamos o valor intermediário de cada quadro.
 */
final class ImeTracker {
    private int current = 0;   // última altura usada
    private int anim = -1;     // altura do quadro atual (-1 = sem animação)

    /** Altura a usar agora, dado o valor que o sistema informa (final). */
    int effective(int systemIme) {
        int v = anim >= 0 ? anim : systemIme;
        current = v;
        return v;
    }

    /** A animação vai começar: segura a altura de antes até o primeiro quadro. */
    void onPrepare() { anim = current; }

    /** Um quadro da animação, com a altura intermediária do teclado. */
    void onProgress(int imeNow) { anim = Math.max(0, imeNow); }

    /** A animação terminou: volta a seguir o valor do sistema. */
    void onEnd() { anim = -1; }
}
