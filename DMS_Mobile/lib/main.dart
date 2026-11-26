import 'package:material_ui/material_ui.dart';

import 'documents.dart';

void main() {
  const baseUrl = String.fromEnvironment(
    'DMS_API_URL',
    defaultValue: 'http://10.0.2.2',
  );
  runApp(DmsApp(api: DocumentApi(Uri.parse(baseUrl))));
}

class DmsApp extends StatelessWidget {
  const DmsApp({super.key, required this.api});

  final DocumentApi api;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'DMS Archive',
      theme: ThemeData(colorSchemeSeed: Colors.indigo),
      darkTheme: ThemeData(
        colorSchemeSeed: Colors.indigo,
        brightness: Brightness.dark,
      ),
      home: _DocumentList(api: api),
    );
  }
}

class _DocumentList extends StatefulWidget {
  const _DocumentList({required this.api});

  final DocumentApi api;

  @override
  State<_DocumentList> createState() => _DocumentListState();
}

class _DocumentListState extends State<_DocumentList> {
  final _search = TextEditingController();
  String _query = '';
  late Future<List<Document>> _documents = widget.api.documents();

  void _load(String query) {
    setState(() {
      _query = query.trim();
      _documents = widget.api.documents(_query);
    });
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('DMS Archive'),
        actions: [
          IconButton(
            tooltip: 'Refresh documents',
            onPressed: () => _load(_query),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              child: TextField(
                controller: _search,
                textInputAction: TextInputAction.search,
                onSubmitted: _load,
                decoration: InputDecoration(
                  labelText: 'Search documents',
                  border: const OutlineInputBorder(),
                  prefixIcon: IconButton(
                    tooltip: 'Search',
                    onPressed: () {
                      FocusScope.of(context).unfocus();
                      _load(_search.text);
                    },
                    icon: const Icon(Icons.search),
                  ),
                  suffixIcon: IconButton(
                    tooltip: 'Clear search',
                    onPressed: () {
                      _search.clear();
                      FocusScope.of(context).unfocus();
                      _load('');
                    },
                    icon: const Icon(Icons.clear),
                  ),
                ),
              ),
            ),
            Expanded(
              child: FutureBuilder(
                future: _documents,
                builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done) {
                    return const _Loading('Loading documents');
                  }
                  if (snapshot.hasError) {
                    return _LoadError(onRetry: () => _load(_query));
                  }
                  final documents = snapshot.data!;
                  if (documents.isEmpty) {
                    return Center(
                      child: Text(
                        _query.isEmpty
                            ? 'No documents yet.'
                            : 'No documents found.',
                      ),
                    );
                  }
                  return ListView.separated(
                    itemCount: documents.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final document = documents[index];
                      return ListTile(
                        leading: const Icon(Icons.description_outlined),
                        title: Text(document.title),
                        subtitle: document.fileName == null
                            ? null
                            : Text(document.fileName!),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => _DocumentDetail(
                              api: widget.api,
                              id: document.id,
                            ),
                          ),
                        ),
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DocumentDetail extends StatefulWidget {
  const _DocumentDetail({required this.api, required this.id});

  final DocumentApi api;
  final int id;

  @override
  State<_DocumentDetail> createState() => _DocumentDetailState();
}

class _DocumentDetailState extends State<_DocumentDetail> {
  late Future<Document> _document = widget.api.document(widget.id);

  void _reload() {
    setState(() => _document = widget.api.document(widget.id));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Document details'),
        actions: [
          IconButton(
            tooltip: 'Refresh document',
            onPressed: _reload,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: SafeArea(
        child: FutureBuilder(
          future: _document,
          builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const _Loading('Loading document');
            }
            if (snapshot.hasError) {
              return _LoadError(onRetry: _reload);
            }
            final document = snapshot.data!;
            final typography = Theme.of(context).textTheme;
            final metadata = {
              'Status': document.statusLabel,
              'File': document.fileName,
              'Type': document.contentType,
              'Size': document.fileSize == null
                  ? null
                  : '${document.fileSize} bytes',
            };
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(document.title, style: typography.headlineSmall),
                if (document.description case final String description) ...[
                  const SizedBox(height: 8),
                  Text(description),
                ],
                const SizedBox(height: 16),
                for (final entry in metadata.entries)
                  if (entry.value case final String value)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: Text('${entry.key}: $value'),
                    ),
                const Divider(height: 32),
                Text('AI summary', style: typography.titleMedium),
                const SizedBox(height: 8),
                SelectableText(document.summary ?? 'No summary available yet.'),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _Loading extends StatelessWidget {
  const _Loading(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Center(child: CircularProgressIndicator(semanticsLabel: label));
  }
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              'Could not load from the DMS server.',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            FilledButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      ),
    );
  }
}
