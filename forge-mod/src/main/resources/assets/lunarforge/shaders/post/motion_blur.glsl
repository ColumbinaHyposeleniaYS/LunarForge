void main() {
    vec4 CurrTexel = SAMPLE(DiffuseSampler, uv);
    vec4 PrevTexel = SAMPLE(PrevSampler, uv);
    float factor = Phosphor.r;

    if (Phosphor.g == 0.0) {
        OUT_COLOR = vec4(max(PrevTexel.rgb * vec3(factor), CurrTexel.rgb), 1.0);
    } else if (Phosphor.g == 1.0) {
        OUT_COLOR = vec4(mix(PrevTexel.rgb, CurrTexel.rgb, factor), 1.0);
    } else {
        PrevTexel.a = max(0.0, min(PrevTexel.a - 0.325, PrevTexel.a * factor * 0.95));

        vec3 blendedRGB = PrevTexel.rgb * PrevTexel.a + CurrTexel.rgb * (1.0 - PrevTexel.a);
        OUT_COLOR = vec4(blendedRGB, 1.0);
    }
}
