package rip.tempest.jenny.anim;

final class KeyframeExpression {
    private final String src;
    private int pos;

    private KeyframeExpression(String src) {
        this.src = src;
    }

    static float eval(String src) {
        try {
            KeyframeExpression expr = new KeyframeExpression(src);
            float value = expr.sum();
            return Float.isFinite(value) ? value : 0f;
        } catch (RuntimeException e) {
            return 0f;
        }
    }

    private float sum() {
        float value = product();
        while (true) {
            if (accept('+')) value += product();
            else if (accept('-')) value -= product();
            else return value;
        }
    }

    private float product() {
        float value = unary();
        while (true) {
            if (accept('*')) value *= unary();
            else if (accept('/')) value /= unary();
            else return value;
        }
    }

    private float unary() {
        if (accept('-')) return -unary();
        if (accept('+')) return unary();
        if (accept('(')) {
            float value = sum();
            accept(')');
            return value;
        }

        skipSpaces();
        int start = pos;
        char first = src.charAt(pos);
        if (Character.isDigit(first) || first == '.') {
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
            return Float.parseFloat(src.substring(start, pos));
        }

        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_' || src.charAt(pos) == '.')) pos++;
        if (pos == start) throw new IllegalArgumentException(src);
        if (accept('(')) {
            int depth = 1;
            while (depth > 0) {
                char c = src.charAt(pos++);
                if (c == '(') depth++;
                else if (c == ')') depth--;
            }
        }
        return 0f;
    }

    private boolean accept(char c) {
        skipSpaces();
        if (pos < src.length() && src.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void skipSpaces() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }
}
