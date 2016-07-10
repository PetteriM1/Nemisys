package org.itxtech.nemisys;

import org.itxtech.nemisys.event.Event;
import org.itxtech.nemisys.network.SourceInterface;
import org.itxtech.nemisys.network.protocol.mcpe.DataPacket;
import org.itxtech.nemisys.network.protocol.mcpe.DisconnectPacket;
import org.itxtech.nemisys.network.protocol.mcpe.PlayerListPacket;
import org.itxtech.nemisys.network.protocol.spp.PlayerLoginPacket;
import org.itxtech.nemisys.network.protocol.spp.PlayerLogoutPacket;
import org.itxtech.nemisys.network.protocol.spp.RedirectPacket;
import org.itxtech.nemisys.utils.TextFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Author: PeratX
 * Nemisys Project
 */
public class Player {
    private PlayerLoginPacket cachedLoginPacket = null;
    private String name;
    private String ip;
    private int port;
    private long clientId;
    private long randomClientId;
    private int protocol;
    private UUID uuid;
    private SourceInterface interfaz;
    private Client client;
    private Server server;
    private byte[] rawUUID;
    private boolean isFirstTimeLogin = true;
    private long lastUpdate;
    private boolean closed;

    public Player(SourceInterface interfaz, long clientId, String ip, int port){
        this.interfaz = interfaz;
        this.clientId = clientId;
        this.ip = ip;
        this.port = port;
        this.name = "Unknown";
        this.server = Server.getInstance();
        this.lastUpdate = System.currentTimeMillis();
    }

    public long getClientId(){
        return this.clientId;
    }

    public byte[] getRawUUID(){
        return this.rawUUID;
    }

    public Server getServer(){
        return this.server;
    }

    public void handleDataPacket(DataPacket packet){
        if(this.closed){
            return;
        }
        this.lastUpdate = System.currentTimeMillis();

        switch (packet.pid()){
            case
        }
    }

    public void redirectPacket(byte[] buffer){
        RedirectPacket pk = new RedirectPacket();
        pk.uuid = this.uuid;
        pk.direct = false;
        pk.mcpeBuffer = buffer;
        this.client.sendDataPacket(pk);
    }

    public String getIp(){
        return this.ip;
    }

    public int getPort(){
        return this.port;
    }

    public UUID getUUID(){
        return this.uuid;
    }

    public String getName(){
        return this.name;
    }

    public void onUpdate(long currentTick){
        if((System.currentTimeMillis() - this.lastUpdate) > 5 * 60 * 1000){//timeout
            this.close("timeout");
        }
    }

    public void removeAllPlayers(){
        PlayerListPacket pk = new PlayerListPacket();
        pk.type = PlayerListPacket.TYPE_REMOVE;
        List<PlayerListPacket.Entry> entries = new ArrayList<>();
        for (Player p : this.client.getPlayers().values()) {
            if (p == this) {
                continue;
            }
            entries.add(new PlayerListPacket.Entry(p.getUUID()));
        }

        pk.entries = entries.stream().toArray(PlayerListPacket.Entry[]::new);
        this.sendDataPacket(pk);
    }

    public void transfer(Client client) {
        this.transfer(client, false);
    }

    public void transfer(Client client, boolean needDisconnect){
        Event ev;
        this.server.getPluginManager().callEvent(ev = new PlayerTransferEvent(this, client, needDisconnect));
        if(!ev.isCancelled()){
            if(this.client != null && needDisconnect){
                PlayerLogoutPacket pk = new PlayerLogoutPacket();
                pk.uuid = this.uuid;
                pk.reason = "Player has been transferred";
                this.client.sendDataPacket(pk);
                this.client.removePlayer(this);
                this.removeAllPlayers();
            }
            this.client = ev.getTargetClient();
            this.client.addPlayer(this);
            PlayerLoginPacket pk = new PlayerLoginPacket();
            pk.uuid = this.uuid;
            pk.address = this.ip;
            pk.port = this.port;
            pk.isFirstTime = this.isFirstTimeLogin;
            pk.cachedLoginPacket = this.cachedLoginPacket.getBuffer();
            this.client.sendDataPacket(pk);

            this.isFirstTimeLogin = false;

            this.server.getLogger().info(this.name + " has been transferred to " + this.client.getIp() + ": + " + this.client.getPort());
        }
    }

    public void sendDataPacket(DataPacket pk){
        this.sendDataPacket(pk, false);
    }

    public void sendDataPacket(DataPacket pk, boolean direct){
        this.sendDataPacket(pk, direct, false);
    }

    public void sendDataPacket(DataPacket pk, boolean direct, boolean needACK){
        this.interfaz.putPacket(this,pk, needACK, direct);
    }

    public void close(){
        this.close("Generic Reason");
    }

    public void close(String reason){
        this.close(reason, true);
    }

    public void close(String reason, boolean notify){
        if(!this.closed){
            if(notify && reason.length() > 0){
                DisconnectPacket pk = new DisconnectPacket();
                pk.message = reason;
                this.sendDataPacket(pk, true);
            }

            this.server.getPluginManager().callEvent(new PlayerLogoutEvent(this));
            this.closed = true;

            if(this.client != null){
                PlayerLogoutPacket pk = new PlayerLogoutPacket();
                pk.uuid = this.uuid;
                pk.reason = reason;
                this.client.sendDataPacket(pk);
                this.client.removePlayer(this);
            }

            this.server.getLogger().info(this.getServer().getLanguage().translateString("synapse.player.logOut", new String[]{
                            TextFormat.AQUA + this.getName() + TextFormat.WHITE,
                            this.ip,
                            String.valueOf(this.port),
                            this.getServer().getLanguage().translateString(reason)
            }));

            this.interfaz.close(this, notify ? reason : "");
            this.getServer().removePlayer(this);
        }
    }

    public int rawHashCode() {
        return super.hashCode();
    }
}