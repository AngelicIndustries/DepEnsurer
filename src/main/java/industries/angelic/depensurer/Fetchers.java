package industries.angelic.depensurer;

/** Finds the full build's {@link Fetcher} by name, so nothing in the shared code refers to it. */
final class Fetchers {

    static final String IMPLEMENTATION = "industries.angelic.depensurer.fetch.Downloader";

    private Fetchers() {}

    /** Null in the CurseForge build. */
    static Fetcher find() {
        try {
            return (Fetcher) Class.forName(IMPLEMENTATION)
                .newInstance();
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException | ClassCastException e) {
            DepEnsurer.LOG.error("Can't create the downloader", e);
            return null;
        }
    }
}
