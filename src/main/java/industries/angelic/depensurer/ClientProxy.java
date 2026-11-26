package industries.angelic.depensurer;

import industries.angelic.depensurer.client.MissingFilesException;
import industries.angelic.depensurer.client.ResourcePacks;
import industries.angelic.depensurer.core.Resolution;

public class ClientProxy extends CommonProxy {

    @Override
    public void serve(Resolution resolution) {
        ResourcePacks.inject(resolution);
    }

    @Override
    public void fail(Report report) {
        throw new MissingFilesException(report);
    }
}
