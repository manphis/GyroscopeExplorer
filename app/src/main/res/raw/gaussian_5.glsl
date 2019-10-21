#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES u_Texture;
varying vec2 vTextureCoord;

void main() {
    vec3 irgb = texture2D(u_Texture, vTextureCoord).rgb;
    float ResS = 150.0;
    float ResT = 150.0;
    vec2 stp0 = vec2(1.0/ResS, 0.0);
    vec2 st0p = vec2(0.0, 1.0/ResT);
    vec2 stpp = vec2(1.0/ResS, 1.0/ResT);
    vec2 stpm = vec2(1.0/ResS, -1.0/ResT);
    vec3 i00 = texture2D(u_Texture, vTextureCoord).rgb;
    vec3 im1m1 = texture2D(u_Texture, vTextureCoord-stpp).rgb;
    vec3 ip1p1 = texture2D(u_Texture, vTextureCoord+stpp).rgb;
    vec3 im1p1 = texture2D(u_Texture, vTextureCoord-stpm).rgb;
    vec3 ip1m1 = texture2D(u_Texture, vTextureCoord+stpm).rgb;
    vec3 im10 = texture2D(u_Texture, vTextureCoord-stp0).rgb;
    vec3 ip10 = texture2D(u_Texture, vTextureCoord+stp0).rgb;
    vec3 i0m1 = texture2D(u_Texture, vTextureCoord-st0p).rgb;
    vec3 i0p1 = texture2D(u_Texture, vTextureCoord+st0p).rgb;

    vec3 e00 = texture2D(u_Texture, vTextureCoord-stpm-stpm).rgb;
    vec3 e01 = texture2D(u_Texture, vTextureCoord-stpm+st0p).rgb;
    vec3 e02 = texture2D(u_Texture, vTextureCoord+st0p+st0p).rgb;
    vec3 e03 = texture2D(u_Texture, vTextureCoord+stpp+st0p).rgb;
    vec3 e04 = texture2D(u_Texture, vTextureCoord+stpp+stpp).rgb;
    vec3 e10 = texture2D(u_Texture, vTextureCoord-stpm-stp0).rgb;
    vec3 e14 = texture2D(u_Texture, vTextureCoord+stpp+stp0).rgb;
    vec3 e20 = texture2D(u_Texture, vTextureCoord-stp0-stp0).rgb;
    vec3 e24 = texture2D(u_Texture, vTextureCoord+stp0+stp0).rgb;
    vec3 e30 = texture2D(u_Texture, vTextureCoord-stpp-stp0).rgb;
    vec3 e34 = texture2D(u_Texture, vTextureCoord+stpm+stp0).rgb;
    vec3 e40 = texture2D(u_Texture, vTextureCoord-stpp-stpp).rgb;
    vec3 e41 = texture2D(u_Texture, vTextureCoord-stpp-st0p).rgb;
    vec3 e42 = texture2D(u_Texture, vTextureCoord-st0p-st0p).rgb;
    vec3 e43 = texture2D(u_Texture, vTextureCoord+stpm-st0p).rgb;
    vec3 e44 = texture2D(u_Texture, vTextureCoord+stpm+stpm).rgb;

    vec3 target = vec3(0.0, 0.0, 0.0);
    target += 16.0*(im1m1+ip1m1+ip1p1+im1p1);
    target += 26.0*(im10+ip10+i0p1+i0m1);
    target += 41.0*(i00);

    target += 1.0*(e00+e04+e40+e44);
    target += 4.0*(e01+e03+e10+e40+e30+e34+e41+e43);
    target += 7.0*(e02+e20+e24+e42);

    target /= 273.0;
    gl_FragColor = vec4(target, 1.0);
}