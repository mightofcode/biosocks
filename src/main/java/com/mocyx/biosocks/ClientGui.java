package com.mocyx.biosocks;

import com.mocyx.biosocks.nio.NioClient;
import com.mocyx.biosocks.util.ConfigDto;
import com.mocyx.biosocks.util.EncodeUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import mdlaf.MaterialLookAndFeel;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import static com.mocyx.biosocks.util.Runner.loadConfig;
import static com.mocyx.biosocks.util.Runner.saveConfig;

@SpringBootApplication
@Slf4j
public class ClientGui extends JFrame{
    ConfigDto configDto ;
    JButton okButton;
    JButton logButton;
    JTextField serverIpField;
    JTextField serverPortField;
    JTextField secretField;
    JTextField localPortField;

    @SneakyThrows
    public ClientGui(){

        UIManager.setLookAndFeel (new MaterialLookAndFeel());

        this.setSize(282,271);
        this.setTitle("Biosocks");
        this.setLayout(null);
        this.setResizable(false);

        this.setLocationRelativeTo(null);


        okButton=new JButton("OK");
        okButton.setBounds(30,188,94, 26);
        this.add(okButton);
        //
         logButton=new JButton("LOG");
        logButton.setBounds(155,188,94, 26);
        this.add(logButton);

        JLabel serverIpLabel=new JLabel("server ip");
        serverIpLabel.setBounds(30,26,100, 24);
        this.add(serverIpLabel);
        //
         serverIpField=new JTextField();
        serverIpField.setBounds(104,26,142, 24);
        this.add(serverIpField);
        //
        JLabel serverPortLabel=new JLabel("server port");
        serverPortLabel.setBounds(30,60,100, 24);
        this.add(serverPortLabel);
        //
        serverPortField=new JTextField();
        serverPortField.setBounds(104,60,142, 24);
        this.add(serverPortField);

        //
        JLabel secretLabel=new JLabel("secret");
        secretLabel.setBounds(30,94,100, 24);
        this.add(secretLabel);
        //
        secretField=new JTextField();
        secretField.setBounds(104,94,142, 24);
        this.add(secretField);

        //
        JLabel localPortLabel=new JLabel("client port");
        localPortLabel.setBounds(30,127,100, 24);
        this.add(localPortLabel);
        //
        localPortField=new JTextField();
        localPortField.setBounds(104,127,142, 24);
        this.add(localPortField);

        afterUiInit();

    }

    Thread workThread ;
    NioClient nioClient;
    private void afterUiInit(){
        configDto = loadConfig("client.json");
        if(configDto==null){
            configDto=new ConfigDto();
        }
        if(configDto.getClientPort()==null){
            configDto.setClientPort(9101);
        }
        if(configDto.getServer()==null){
            configDto.setSecret("0.0.0.0");
        }
        if(configDto.getServerPort()==null){
            configDto.setServerPort(9501);
        }
        if(configDto.getSecret()==null){
            configDto.setSecret("123456");
        }
        this.localPortField.setText(configDto.getClientPort().toString());
        this.serverIpField.setText(configDto.getServer());
        this.serverPortField.setText(configDto.getServerPort().toString());
        this.secretField.setText(configDto.getSecret());
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveConfigFromFrom();
                EncodeUtil.setSecret(configDto.getSecret());
                if(workThread==null){
                    nioClient = new NioClient(configDto);
                    workThread = new Thread(nioClient);
                    workThread.start();
                    ClientGui.this.setTitle("Biosocks 运行中");
                }else {
                    nioClient.closeFlag.set(true);
                    try {
                        workThread.join();
                    } catch (InterruptedException ex) {
                        log.error(ex.getMessage(),e);
                    }
                    //
                    nioClient = new NioClient(configDto);
                    workThread = new Thread(nioClient);
                    workThread.start();
                    System.currentTimeMillis();
                }
            }
        });
        this.logButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Runtime.getRuntime().exec("notepad .\\logs\\biosocks.log");
                }catch (Exception exc){
                    log.error(exc.getMessage(),exc);
                }
            }
        });
    }

    private void saveConfigFromFrom(){
        try {
            configDto.setClientPort(Integer.parseInt(this.localPortField.getText()));
            configDto.setServer(this.serverIpField.getText());
            configDto.setServerPort(Integer.parseInt(this.serverPortField.getText()));
            configDto.setSecret(this.secretField.getText());
            saveConfig("client.json",configDto);
        }catch (Exception e){
            JOptionPane.showMessageDialog(this,
                    "格式错误",
                    "Error",
                    JOptionPane.WARNING_MESSAGE);
            log.error(e.getMessage(),e);
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ClientGui.class)
                .headless(false).run(args);
        EventQueue.invokeLater(() -> {
            ClientGui ex = ctx.getBean(ClientGui.class);
            ex.setVisible(true);
        });
    }
}
