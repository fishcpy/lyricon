package io.github.proify.lyricon.provider;

import io.github.proify.lyricon.provider.IRemoteActivePlayerService;

interface IActivePlayerBinder {
    void onRegistrationCallback(IRemoteActivePlayerService service);
}
