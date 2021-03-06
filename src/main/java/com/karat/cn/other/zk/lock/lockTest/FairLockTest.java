package com.karat.cn.other.zk.lock.lockTest;

import com.google.common.base.Strings;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FairLockTest {

    private String zkQurom = "47.107.121.215:2181";

    private String lockName = "/LOCKS";

    private String lockZnode = null;

    private ZooKeeper zk;

    public FairLockTest(){
        try {
            zk = new ZooKeeper(zkQurom, 6000, new Watcher() {
                @Override
                public void process(WatchedEvent watchedEvent) {
                    System.out.println("Receive event "+watchedEvent);
                    if(Event.KeeperState.SyncConnected == watchedEvent.getState())
                        System.out.println("connection is established...");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void ensureRootPath(){
        try {
            if (zk.exists(lockName,true)==null){
                zk.create(lockName,"".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * 获取锁
     * @return
     * @throws InterruptedException
     */
    public void lock(){
        String path = null;
        ensureRootPath();
            try {
                path = zk.create(lockName+"/mylock_", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
                lockZnode = path;
                List<String> minPath = zk.getChildren(lockName,false);
                Collections.sort(minPath);
                if (!Strings.nullToEmpty(path).trim().isEmpty()&&!Strings.nullToEmpty(minPath.get(0)).trim().isEmpty()&&path.equals(lockName+"/"+minPath.get(0))) {
                    System.out.println(Thread.currentThread().getName() + "得到锁");
                    return;
                }
                String watchNode = null;
                for (int i=minPath.size()-1;i>=0;i--){
                    if(minPath.get(i).compareTo(path.substring(path.lastIndexOf("/") + 1))<0){
                        watchNode = minPath.get(i);
                        break;
                    }
                }

                if (watchNode!=null){
                    final String watchNodeTmp = watchNode;
                    final Thread thread = Thread.currentThread();
                    Stat stat = zk.exists(lockName + "/" + watchNodeTmp,new Watcher() {
                        @Override
                        public void process(WatchedEvent watchedEvent) {
                            if(watchedEvent.getType() == Event.EventType.NodeDeleted){
                                thread.interrupt();
                            }
                            try {
                                zk.exists(lockName + "/" + watchNodeTmp,true);
                            } catch (KeeperException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                    });
                    if(stat != null){
                        System.out.println("Thread " + Thread.currentThread().getId() + " 等待 from " + lockName + "/" + watchNode);
                    }
                }
                try {
                    Thread.sleep(1000000000);
                }catch (InterruptedException ex){
                    System.out.println(Thread.currentThread().getName() + "得到锁");
                    return;
                }

            } catch (Exception e) {
               e.printStackTrace();
            }
    }

    /**
     * 释放锁
     */
    public void unlock(){
        try {
            System.out.println(Thread.currentThread().getName() +  "删除成功");
            zk.delete(lockZnode,-1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }



    public static void main(String args[]) throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(10);
        for (int i = 0;i<4;i++){
            service.execute(()-> {
                FairLockTest test = new FairLockTest();
                try {
                    test.lock();
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                test.unlock();
            });
        }
        service.shutdown();
    }

}

