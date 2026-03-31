package io.github.proify.lyricon.provider;

import io.github.proify.lyricon.provider.IActivePlayerListener;

interface IRemoteActivePlayerService {
    void addActivePlayerListener(IActivePlayerListener listener);
    void removeActivePlayerListener(IActivePlayerListener listener);
    void disconnect();
}
