import 'package:flutter/material.dart';

import '../../theme/app_theme.dart';

/// Placeholder for a tab whose screen is not built yet.
class ComingSoonScreen extends StatelessWidget {
  const ComingSoonScreen({super.key, required this.title, required this.note});

  final String title;
  final String note;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(title, style: AppTheme.heading(22))),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.construction_outlined, size: 40, color: AppColors.muted),
              const SizedBox(height: 12),
              Text(note, textAlign: TextAlign.center, style: const TextStyle(color: AppColors.muted)),
            ]),
          ),
        ),
      );
}
