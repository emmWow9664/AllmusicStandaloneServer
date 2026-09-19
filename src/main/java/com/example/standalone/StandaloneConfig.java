package com.example.standalone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 独立服务端的额外配置（端口等）
 */
public class StandaloneConfig {
    /**
     * 服务端监听的端口
     */
    public int port = 5223;
    /**
     * 绑定地址
     */
    public String bindHost = "0.0.0.0";

    /**
     * 独立服务端配置文件（位于服务端所在文件夹）
     */
    public static File getConfigFile() {
        return new File(Main.getBaseDir(), "standalone_config.json");
    }

    public static StandaloneConfig load() {
        StandaloneConfig config = new StandaloneConfig();
        if (!getConfigFile().exists()) {
            config.save();
            return config;
        }
        try {
            InputStreamReader reader = new InputStreamReader(
                    Files.newInputStream(getConfigFile().toPath()), StandardCharsets.UTF_8);
            BufferedReader bf = new BufferedReader(reader);
            StandaloneConfig loaded = new Gson().fromJson(bf, StandaloneConfig.class);
            bf.close();
            reader.close();
            if (loaded != null && loaded.port > 0 && loaded.port < 65536) {
                config = loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return config;
    }

    public void save() {
        try {
            String data = new GsonBuilder().setPrettyPrinting().create().toJson(this);
            FileOutputStream out = new FileOutputStream(getConfigFile());
            OutputStreamWriter write = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            write.write(data);
            write.close();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
