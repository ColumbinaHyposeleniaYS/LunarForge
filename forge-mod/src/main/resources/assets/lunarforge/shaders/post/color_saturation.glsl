void applyHue(inout vec4 color, float hue) {
    float angle = radians(hue);
    vec3 k = vec3(0.57735, 0.57735, 0.57735);
    float cosAngle = cos(angle);
    color.rgb = color.rgb * cosAngle + cross(k, color.rgb) * sin(angle) + k * dot(k, color.rgb) * (1.0 - cosAngle);
}

void applySaturation(inout vec4 color, float saturation) {
    float luma = sqrt(color.r * color.r * 0.299 + color.g * color.g * 0.587 + color.b * color.b * 0.114);
    color.r = luma + (color.r - luma) * saturation;
    color.g = luma + (color.g - luma) * saturation;
    color.b = luma + (color.b - luma) * saturation;
}

vec4 applyHSBCEffect(vec4 startColor, vec4 hsbc) {
    float _Hue = 360.0 * hsbc.r;
    float _Brightness = hsbc.b * 2.0 - 1.0;
    float _Contrast = hsbc.a * 2.0;
    float _Saturation = hsbc.g * 2.0;

    vec4 outputColor = startColor;
    applyHue(outputColor, _Hue);
    outputColor.rgb = (outputColor.rgb - 0.5) * _Contrast + 0.5;
    outputColor.rgb = outputColor.rgb + vec3(_Brightness, _Brightness, _Brightness);
    applySaturation(outputColor, _Saturation);

    outputColor = vec4(outputColor.rgb, 1.0);

    return outputColor;
}

void main() {
    OUT_COLOR = applyHSBCEffect(SAMPLE(DiffuseSampler, uv), vec4(Hue, Saturation, Brightness, Contrast));
}
