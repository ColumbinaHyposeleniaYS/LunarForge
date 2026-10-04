package com.example.lunarforge.module.render;

public interface ParticleTag {
    int lunarforge$type();
    void lunarforge$setType(int legacyId);
    float lunarforge$red();
    float lunarforge$green();
    float lunarforge$blue();
    float lunarforge$alpha();
    float lunarforge$scale();
    void lunarforge$setColor(float red, float green, float blue, float alpha);
    void lunarforge$setScale(float scale);
}
