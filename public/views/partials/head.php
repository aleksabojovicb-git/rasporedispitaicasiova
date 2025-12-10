<!DOCTYPE html>
<html lang="bs">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= $pageTitle ?? "FTN Sistem" ?></title>

    <link rel="stylesheet" href="../assets/css/base/variables.css">
    <link rel="stylesheet" href="../assets/css/base/base.css">

    <link rel="stylesheet" href="../assets/css/components/buttons.css">
    <link rel="stylesheet" href="../assets/css/components/forms.css">
    <link rel="stylesheet" href="../assets/css/components/cards.css">
    <link rel="stylesheet" href="../assets/css/components/tables.css">
    <link rel="stylesheet" href="../assets/css/components/tabs.css">
    <link rel="stylesheet" href="../assets/css/components/stacks.css">
    <link rel="stylesheet" href="../assets/css/components/modals.css">

    <link rel="stylesheet" href="../assets/css/layouts/public_layout.css">
    <link rel="stylesheet" href="../assets/css/layouts/admin_layout.css">
    <link rel="stylesheet" href="../assets/css/layouts/admin_header.css">


    <!-- ===== PAGE SPECIFIC (optional) ===== -->
    <?php if (!empty($pageCss)): ?>
        <link rel="stylesheet" href="../assets/css/pages/<?= $pageCss ?>.css">
    <?php endif; ?>
</head>
<body>
