"#extension GL_OES_EGL_image_external : require
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

    vec3 target = vec3(0.0, 0.0, 0.0);
    target += 1.0*(im1m1+ip1m1+ip1p1+im1p1);
    target += 2.0*(im10+ip10+i0p1+i0m1);
    target += 4.0*(i00);

    target /= 16.0;
    gl_FragColor = vec4(target, 1.0);
}