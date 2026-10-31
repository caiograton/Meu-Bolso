package com.caiograton.meubolso;

/**
 * Contas (em pixels, na tela) para o app nunca ficar por baixo das barras do
 * Android nem do teclado, sem contar o mesmo espaço duas vezes.
 * Se o sistema já encolheu a janela por causa do teclado, não sobra nada a
 * compensar; se não encolheu, o espaço do teclado vira margem inferior.
 */
final class InsetMath {
    private InsetMath() {}

    /** Margem inferior: quanto do conteúdo ainda invade a barra ou o teclado. */
    static int bottomPad(int screenH, int contentBottom, int sysBottom, int imeBottom) {
        int limit = screenH - Math.max(sysBottom, imeBottom);
        return Math.max(0, contentBottom - limit);
    }

    /** Margem superior: quanto do conteúdo ainda invade a barra de status. */
    static int topPad(int contentTop, int sysTop) {
        return Math.max(0, sysTop - contentTop);
    }

    /** Faixa preta sob o app: some quando o teclado está aberto. */
    static int bottomBarHeight(boolean imeVisible, int sysBottom) {
        return imeVisible ? 0 : sysBottom;
    }
}
