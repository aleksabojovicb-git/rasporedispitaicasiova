<?php
require_once 'dbconnection.php';

class JavaBridge {
    private $javaPath;
    private $classPath;
    private $conn;
    
    public function __construct($dbConnection) {
        $this->conn = $dbConnection;
        $this->javaPath = 'java';
        $this->classPath = __DIR__ . '/../java';
    }
    
    private function formatErrorResponse($message) {
        return array(
            'status' => 'error',
            'message' => $message
        );
    }
    
    private function formatSuccessResponse($message) {
        return array(
            'status' => 'success',
            'message' => $message
        );
    }
    
    public function dodajPredavanje($idPredmet, $idSala, $idProfesor, $dan, $vremeOd, $vremeDo) {
        $idPredmet = intval($idPredmet);
        $idSala = intval($idSala);
        $idProfesor = intval($idProfesor);
        $dan = trim($dan);
        $vremeOd = trim($vremeOd);
        $vremeDo = trim($vremeDo);
        
        if (empty($dan) || empty($vremeOd) || empty($vremeDo)) {
            return $this->formatErrorResponse('Sva polja moraju biti popunjena');
        }
        
        $command = sprintf(
            '%s -cp "%s" ValidacijaTermina dodajPredavanje %d %d %d %s %s %s',
            $this->javaPath,
            $this->classPath,
            $idPredmet,
            $idSala,
            $idProfesor,
            escapeshellarg($dan),
            escapeshellarg($vremeOd),
            escapeshellarg($vremeDo)
        );
        
        $output = array();
        $returnCode = 0;
        exec($command, $output, $returnCode);
        
        $result = implode("\n", $output);
        
        if (strpos($result, 'OK:') === 0) {
            return $this->formatSuccessResponse(substr($result, 4));
        } else {
            return $this->formatErrorResponse(substr($result, 7));
        }
    }
    
    public function dodajVjezbe($idPredmet, $idSala, $idProfesor, $dan, $vremeOd, $vremeDo) {
        $idPredmet = intval($idPredmet);
        $idSala = intval($idSala);
        $idProfesor = intval($idProfesor);
        $dan = trim($dan);
        $vremeOd = trim($vremeOd);
        $vremeDo = trim($vremeDo);
        
        if (empty($dan) || empty($vremeOd) || empty($vremeDo)) {
            return $this->formatErrorResponse('Sva polja moraju biti popunjena');
        }
        
        $command = sprintf(
            '%s -cp "%s" ValidacijaTermina dodajVjezbe %d %d %d %s %s %s',
            $this->javaPath,
            $this->classPath,
            $idPredmet,
            $idSala,
            $idProfesor,
            escapeshellarg($dan),
            escapeshellarg($vremeOd),
            escapeshellarg($vremeDo)
        );
        
        $output = array();
        $returnCode = 0;
        exec($command, $output, $returnCode);
        
        $result = implode("\n", $output);
        
        if (strpos($result, 'OK:') === 0) {
            return $this->formatSuccessResponse(substr($result, 4));
        } else {
            return $this->formatErrorResponse(substr($result, 7));
        }
    }
    
    public function dodajKolokvijum($idPredmet, $idSala, $idProfesor, $idDezurni, $datum, $vremeOd, $vremeDo) {
        $idPredmet = intval($idPredmet);
        $idSala = intval($idSala);
        $idProfesor = intval($idProfesor);
        $idDezurni = intval($idDezurni);
        $datum = trim($datum);
        $vremeOd = trim($vremeOd);
        $vremeDo = trim($vremeDo);
        
        if (empty($datum) || empty($vremeOd) || empty($vremeDo)) {
            return $this->formatErrorResponse('Sva polja moraju biti popunjena');
        }
        
        $command = sprintf(
            '%s -cp "%s" ValidacijaTermina dodajKolokvijum %d %d %d %d %s %s %s',
            $this->javaPath,
            $this->classPath,
            $idPredmet,
            $idSala,
            $idProfesor,
            $idDezurni,
            escapeshellarg($datum),
            escapeshellarg($vremeOd),
            escapeshellarg($vremeDo)
        );
        
        $output = array();
        $returnCode = 0;
        exec($command, $output, $returnCode);
        
        $result = implode("\n", $output);
        
        if (strpos($result, 'OK:') === 0) {
            return $this->formatSuccessResponse(substr($result, 4));
        } else {
            return $this->formatErrorResponse(substr($result, 7));
        }
    }
    
    public function dodajIspit($idPredmet, $idSala, $idProfesor, $datum, $vremeOd, $vremeDo, $tipIspita) {
        $idPredmet = intval($idPredmet);
        $idSala = intval($idSala);
        $idProfesor = intval($idProfesor);
        $datum = trim($datum);
        $vremeOd = trim($vremeOd);
        $vremeDo = trim($vremeDo);
        $tipIspita = trim($tipIspita);
        
        if (empty($datum) || empty($vremeOd) || empty($vremeDo) || empty($tipIspita)) {
            return $this->formatErrorResponse('Sva polja moraju biti popunjena');
        }
        
        $command = sprintf(
            '%s -cp "%s" ValidacijaTermina dodajIspit %d %d %d %s %s %s %s',
            $this->javaPath,
            $this->classPath,
            $idPredmet,
            $idSala,
            $idProfesor,
            escapeshellarg($datum),
            escapeshellarg($vremeOd),
            escapeshellarg($vremeDo),
            escapeshellarg($tipIspita)
        );
        
        $output = array();
        $returnCode = 0;
        exec($command, $output, $returnCode);
        
        $result = implode("\n", $output);
        
        if (strpos($result, 'OK:') === 0) {
            return $this->formatSuccessResponse(substr($result, 4));
        } else {
            return $this->formatErrorResponse(substr($result, 7));
        }
    }
    
    public function generisiRasporedPredavanja($idPredmet) {
        $idPredmet = intval($idPredmet);
        
        $command = sprintf(
            '%s -cp "%s" ValidacijaTermina generisiPredavanja %d',
            $this->javaPath,
            $this->classPath,
            $idPredmet
        );
        
        $output = array();
        $returnCode = 0;
        exec($command, $output, $returnCode);
        
        $result = implode("\n", $output);
        
        if (strpos($result, 'OK:') === 0 || strpos($result, 'UPOZORENJE:') === 0) {
            return $this->formatSuccessResponse($result);
        } else {
            return $this->formatErrorResponse(substr($result, 7));
        }
    }
    
    public function generisiRasporedVjezbi($idPredmet) {
        $idPredmet = intval($idPredmet);
        
        $command = sprintf(
            '%s -cp "%s" ValidacijaTermina generisiVjezbe %d',
            $this->javaPath,
            $this->classPath,
            $idPredmet
        );
        
        $output = array();
        $returnCode = 0;
        exec($command, $output, $returnCode);
        
        $result = implode("\n", $output);
        
        if (strpos($result, 'OK:') === 0 || strpos($result, 'UPOZORENJE:') === 0) {
            return $this->formatSuccessResponse($result);
        } else {
            return $this->formatErrorResponse(substr($result, 7));
        }
    }
}
?>
