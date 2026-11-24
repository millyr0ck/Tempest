package rip.tempest.interfaces.click.bankroll;

public interface BankrollCanvas {
    void quad(float x0, float y0, float x1, float y1, int topLeft, int topRight, int bottomRight, int bottomLeft,
              int clipX0, int clipY0, int clipX1, int clipY1);

    void glyph(BankrollFont font, int x, int y, int width, int height, int u, int v, int color,
               int clipX0, int clipY0, int clipX1, int clipY1);
}
