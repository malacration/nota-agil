package br.andrew.nota.agil

import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.qive.model.ConhecimentoTransporte
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class ConhecimentoTransporteTest {

    @Test
    fun `json real`() {
        val json = "{\n" +
                "      \"access_key\": \"11260154984461000175570010001741451001439303\",\n" +
                "      \"xml\": {\n" +
                "        \"@attributes\": {\n" +
                "          \"versao\": \"4.00\"\n" +
                "        },\n" +
                "        \"CTe\": {\n" +
                "          \"infCte\": {\n" +
                "            \"@attributes\": {\n" +
                "              \"Id\": \"CTe11260154984461000175570010001741451001439303\",\n" +
                "              \"versao\": \"4.00\"\n" +
                "            },\n" +
                "            \"ide\": {\n" +
                "              \"cUF\": \"11\",\n" +
                "              \"cCT\": \"00143930\",\n" +
                "              \"CFOP\": \"5353\",\n" +
                "              \"natOp\": \"Transp a est comercial\",\n" +
                "              \"mod\": \"57\",\n" +
                "              \"serie\": \"1\",\n" +
                "              \"nCT\": \"174145\",\n" +
                "              \"dhEmi\": \"2026-01-21T08:57:41-04:00\",\n" +
                "              \"tpImp\": \"1\",\n" +
                "              \"tpEmis\": \"1\",\n" +
                "              \"cDV\": \"3\",\n" +
                "              \"tpAmb\": \"1\",\n" +
                "              \"tpCTe\": \"0\",\n" +
                "              \"procEmi\": \"0\",\n" +
                "              \"verProc\": \"1.0\",\n" +
                "              \"cMunEnv\": \"1100288\",\n" +
                "              \"xMunEnv\": \"ROLIM DE MOURA\",\n" +
                "              \"UFEnv\": \"RO\",\n" +
                "              \"modal\": \"01\",\n" +
                "              \"tpServ\": \"0\",\n" +
                "              \"cMunIni\": \"1100205\",\n" +
                "              \"xMunIni\": \"PORTO VELHO\",\n" +
                "              \"UFIni\": \"RO\",\n" +
                "              \"cMunFim\": \"1100304\",\n" +
                "              \"xMunFim\": \"VILHENA\",\n" +
                "              \"UFFim\": \"RO\",\n" +
                "              \"retira\": \"1\",\n" +
                "              \"indIEToma\": \"1\",\n" +
                "              \"toma3\": {\n" +
                "                \"toma\": \"0\"\n" +
                "              }\n" +
                "            },\n" +
                "            \"compl\": {\n" +
                "              \"Entrega\": {\n" +
                "                \"comData\": {\n" +
                "                  \"tpPer\": \"2\",\n" +
                "                  \"dProg\": \"2026-01-27\"\n" +
                "                },\n" +
                "                \"semHora\": {\n" +
                "                  \"tpHor\": \"0\"\n" +
                "                }\n" +
                "              },\n" +
                "              \"xObs\": \"CST: 40 - Apolice seguro: 028522025000106540013777 - Seguradora: 19323190000106 AXA SEGUROS S/A\",\n" +
                "              \"ObsCont\": [\n" +
                "                {\n" +
                "                  \"@attributes\": {\n" +
                "                    \"xCampo\": \"1\"\n" +
                "                  },\n" +
                "                  \"xTexto\": \"CST: 40 - Apolice seguro: 028522025000106540013777 - Seguradora: 19323190000106 AXA SEGUROS S/A\"\n" +
                "                },\n" +
                "                {\n" +
                "                  \"@attributes\": {\n" +
                "                    \"xCampo\": \"RESPSEG\"\n" +
                "                  },\n" +
                "                  \"xTexto\": \"54984461000175\"\n" +
                "                },\n" +
                "                {\n" +
                "                  \"@attributes\": {\n" +
                "                    \"xCampo\": \"3\"\n" +
                "                  },\n" +
                "                  \"xTexto\": \"TABELA: PERCENTUAL RE322574 - ROTA: PVHP/VLHP - TARIF: 070 - TIPO MERCAD: DIVERSOS\"\n" +
                "                },\n" +
                "                {\n" +
                "                  \"@attributes\": {\n" +
                "                    \"xCampo\": \"4\"\n" +
                "                  },\n" +
                "                  \"xTexto\": \"Tratamento de dados pessoais pode ser dado para execucao de contrato de transporte (LGPD art. 7, V).\"\n" +
                "                }\n" +
                "              ]\n" +
                "            },\n" +
                "            \"emit\": {\n" +
                "              \"CNPJ\": \"54984461000175\",\n" +
                "              \"IE\": \"00000006968309\",\n" +
                "              \"xNome\": \"PFS TRANSPORTES LTDA\",\n" +
                "              \"enderEmit\": {\n" +
                "                \"xLgr\": \"AV NORTE SUL\",\n" +
                "                \"nro\": \"6724\",\n" +
                "                \"xBairro\": \"OLIMPICO\",\n" +
                "                \"cMun\": \"1100288\",\n" +
                "                \"xMun\": \"ROLIM DE MOURA\",\n" +
                "                \"CEP\": \"76940000\",\n" +
                "                \"UF\": \"RO\",\n" +
                "                \"fone\": \"69993481693\"\n" +
                "              },\n" +
                "              \"CRT\": \"3\"\n" +
                "            },\n" +
                "            \"rem\": {\n" +
                "              \"CNPJ\": \"05925052000516\",\n" +
                "              \"IE\": \"00000005470439\",\n" +
                "              \"xNome\": \"FAZENDA RIO MADEIRA S/A (GRUPO\",\n" +
                "              \"fone\": \"6932182000\",\n" +
                "              \"enderReme\": {\n" +
                "                \"xLgr\": \"RODOVIA BR-364\",\n" +
                "                \"nro\": \"9100\",\n" +
                "                \"xCpl\": \"PAVMTOSSUPERIOR\",\n" +
                "                \"xBairro\": \"AEROCLUBE\",\n" +
                "                \"cMun\": \"1100205\",\n" +
                "                \"xMun\": \"PORTO VELHO\",\n" +
                "                \"CEP\": \"76816800\",\n" +
                "                \"UF\": \"RO\",\n" +
                "                \"cPais\": \"1058\",\n" +
                "                \"xPais\": \"BRASIL\"\n" +
                "              }\n" +
                "            },\n" +
                "            \"exped\": {\n" +
                "              \"CNPJ\": \"05925052000516\",\n" +
                "              \"IE\": \"00000005470439\",\n" +
                "              \"xNome\": \"FAZENDA RIO MADEIRA S/A (GRUPO\",\n" +
                "              \"fone\": \"6932182000\",\n" +
                "              \"enderExped\": {\n" +
                "                \"xLgr\": \"RODOVIA BR-364\",\n" +
                "                \"nro\": \"9100\",\n" +
                "                \"xCpl\": \"PAVMTOSSUPERIOR\",\n" +
                "                \"xBairro\": \"AEROCLUBE\",\n" +
                "                \"cMun\": \"1100205\",\n" +
                "                \"xMun\": \"PORTO VELHO\",\n" +
                "                \"CEP\": \"76816800\",\n" +
                "                \"UF\": \"RO\",\n" +
                "                \"cPais\": \"1058\",\n" +
                "                \"xPais\": \"BRASIL\"\n" +
                "              }\n" +
                "            },\n" +
                "            \"receb\": {\n" +
                "              \"CNPJ\": \"22568981000100\",\n" +
                "              \"IE\": \"00000004353790\",\n" +
                "              \"xNome\": \"TECNODIESEL VILHENA LTDA\",\n" +
                "              \"fone\": \"6933228521\",\n" +
                "              \"enderReceb\": {\n" +
                "                \"xLgr\": \"AVENIDA AV MARECHAL RONDON\",\n" +
                "                \"nro\": \"7204\",\n" +
                "                \"xBairro\": \"PQ INDUSTRIAL\",\n" +
                "                \"cMun\": \"1100304\",\n" +
                "                \"xMun\": \"VILHENA\",\n" +
                "                \"CEP\": \"76987832\",\n" +
                "                \"UF\": \"RO\",\n" +
                "                \"cPais\": \"1058\",\n" +
                "                \"xPais\": \"BRASIL\"\n" +
                "              }\n" +
                "            },\n" +
                "            \"dest\": {\n" +
                "              \"CNPJ\": \"22568981000100\",\n" +
                "              \"IE\": \"00000004353790\",\n" +
                "              \"xNome\": \"TECNODIESEL VILHENA LTDA\",\n" +
                "              \"fone\": \"6933228521\",\n" +
                "              \"ISUF\": \"200911171\",\n" +
                "              \"enderDest\": {\n" +
                "                \"xLgr\": \"AVENIDA AV. MARECHAL RONDON\",\n" +
                "                \"nro\": \"7204\",\n" +
                "                \"xBairro\": \"PQ INDUSTRIAL\",\n" +
                "                \"cMun\": \"1100304\",\n" +
                "                \"xMun\": \"VILHENA\",\n" +
                "                \"CEP\": \"76987832\",\n" +
                "                \"UF\": \"RO\",\n" +
                "                \"cPais\": \"1058\",\n" +
                "                \"xPais\": \"BRASIL\"\n" +
                "              }\n" +
                "            },\n" +
                "            \"vPrest\": {\n" +
                "              \"vTPrest\": \"285.00\",\n" +
                "              \"vRec\": \"285.00\",\n" +
                "              \"Comp\": [\n" +
                "                {\n" +
                "                  \"xNome\": \"FRETE VALOR\",\n" +
                "                  \"vComp\": \"280.00\"\n" +
                "                },\n" +
                "                {\n" +
                "                  \"xNome\": \"PEDAGIO\",\n" +
                "                  \"vComp\": \"5.00\"\n" +
                "                }\n" +
                "              ]\n" +
                "            },\n" +
                "            \"imp\": {\n" +
                "              \"ICMS\": {\n" +
                "                \"ICMS45\": {\n" +
                "                  \"CST\": \"40\"\n" +
                "                }\n" +
                "              },\n" +
                "              \"vTotTrib\": \"0.00\",\n" +
                "              \"ICMSUFFim\": {\n" +
                "                \"vBCUFFim\": \"0.00\",\n" +
                "                \"pFCPUFFim\": \"0.00\",\n" +
                "                \"pICMSUFFim\": \"0.00\",\n" +
                "                \"pICMSInter\": \"0.00\",\n" +
                "                \"vFCPUFFim\": \"0.00\",\n" +
                "                \"vICMSUFFim\": \"0.00\",\n" +
                "                \"vICMSUFIni\": \"0.00\"\n" +
                "              },\n" +
                "              \"IBSCBS\": {\n" +
                "                \"CST\": \"000\",\n" +
                "                \"cClassTrib\": \"000001\",\n" +
                "                \"gIBSCBS\": {\n" +
                "                  \"vBC\": \"274.60\",\n" +
                "                  \"gIBSUF\": {\n" +
                "                    \"pIBSUF\": \"0.10\",\n" +
                "                    \"vIBSUF\": \"0.27\"\n" +
                "                  },\n" +
                "                  \"gIBSMun\": {\n" +
                "                    \"pIBSMun\": \"0.00\",\n" +
                "                    \"vIBSMun\": \"0.00\"\n" +
                "                  },\n" +
                "                  \"vIBS\": \"0.27\",\n" +
                "                  \"gCBS\": {\n" +
                "                    \"pCBS\": \"0.90\",\n" +
                "                    \"vCBS\": \"2.47\"\n" +
                "                  }\n" +
                "                }\n" +
                "              },\n" +
                "              \"vTotDFe\": \"285.00\"\n" +
                "            },\n" +
                "            \"infCTeNorm\": {\n" +
                "              \"infCarga\": {\n" +
                "                \"vCarga\": \"14000.00\",\n" +
                "                \"proPred\": \"MODULO MOTOR\",\n" +
                "                \"infQ\": [\n" +
                "                  {\n" +
                "                    \"cUnid\": \"03\",\n" +
                "                    \"tpMed\": \"UNIDADE\",\n" +
                "                    \"qCarga\": \"1\"\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"cUnid\": \"03\",\n" +
                "                    \"tpMed\": \"PARES\",\n" +
                "                    \"qCarga\": \"0\"\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"cUnid\": \"00\",\n" +
                "                    \"tpMed\": \"M3\",\n" +
                "                    \"qCarga\": \"0.0000\"\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"cUnid\": \"01\",\n" +
                "                    \"tpMed\": \"PESO REAL\",\n" +
                "                    \"qCarga\": \"8.0000\"\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"cUnid\": \"01\",\n" +
                "                    \"tpMed\": \"PESO BASE DE CALCULO\",\n" +
                "                    \"qCarga\": \"8.0000\"\n" +
                "                  }\n" +
                "                ],\n" +
                "                \"vCargaAverb\": \"14000.00\"\n" +
                "              },\n" +
                "              \"infDoc\": {\n" +
                "                \"infNFe\": {\n" +
                "                  \"chave\": \"11260105925052000516550030000000181348686450\"\n" +
                "                }\n" +
                "              },\n" +
                "              \"infModal\": {\n" +
                "                \"@attributes\": {\n" +
                "                  \"versaoModal\": \"4.00\"\n" +
                "                },\n" +
                "                \"rodo\": {\n" +
                "                  \"RNTRC\": \"56912566\"\n" +
                "                }\n" +
                "              }\n" +
                "            },\n" +
                "            \"infRespTec\": {\n" +
                "              \"CNPJ\": \"01446320000132\",\n" +
                "              \"xContato\": \"Suporte SSW\",\n" +
                "              \"email\": \"suporte@ssw.inf.br\",\n" +
                "              \"fone\": \"04133360877\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"infCTeSupl\": {\n" +
                "            \"qrCodCTe\": \"https://dfe-portal.svrs.rs.gov.br/cte/qrCode?chCTe=11260154984461000175570010001741451001439303&tpAmb=1\"\n" +
                "          },\n" +
                "          \"Signature\": {\n" +
                "            \"SignedInfo\": {\n" +
                "              \"CanonicalizationMethod\": {\n" +
                "                \"@attributes\": {\n" +
                "                  \"Algorithm\": \"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"\n" +
                "                }\n" +
                "              },\n" +
                "              \"SignatureMethod\": {\n" +
                "                \"@attributes\": {\n" +
                "                  \"Algorithm\": \"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"\n" +
                "                }\n" +
                "              },\n" +
                "              \"Reference\": {\n" +
                "                \"@attributes\": {\n" +
                "                  \"URI\": \"#CTe11260154984461000175570010001741451001439303\"\n" +
                "                },\n" +
                "                \"Transforms\": {\n" +
                "                  \"Transform\": [\n" +
                "                    {\n" +
                "                      \"@attributes\": {\n" +
                "                        \"Algorithm\": \"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"\n" +
                "                      }\n" +
                "                    },\n" +
                "                    {\n" +
                "                      \"@attributes\": {\n" +
                "                        \"Algorithm\": \"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"\n" +
                "                      }\n" +
                "                    }\n" +
                "                  ]\n" +
                "                },\n" +
                "                \"DigestMethod\": {\n" +
                "                  \"@attributes\": {\n" +
                "                    \"Algorithm\": \"http://www.w3.org/2000/09/xmldsig#sha1\"\n" +
                "                  }\n" +
                "                },\n" +
                "                \"DigestValue\": \"BCjrv+aKf7cyJfXtraBNqzBNchs=\"\n" +
                "              }\n" +
                "            },\n" +
                "            \"SignatureValue\": \"UPNX7eyQdzhXoA0PmALj7U9/8daarQuyd/WPUwlDXwOHVDk1vo9HEUuFJ4cIxfMupwxwh9wup93TtFIjnMTEF/a1s09S0oe8MJVlEy+qrlNnZ45GXyHeLfQ2zOjHSTl3uzRGZ/Y6X0YNW8RjJCyvFI61Ou4FwaTlalxqxt2FUzzLpNdFssdPi/3GaQpDdgSlT0/0KrZ3sAK7VOnCkDyGIzaGzHV4Dde1hUdrT2mlEteZbxuLzY3aPVGQAazsHx3N1AvfDX+DtfybxgcAUE0lZh3g5PMC3k762svIEois/3Z/qwrbAOavw6rB//vHluzCgCcznF0/gURHnG6hPKuJ1w==\",\n" +
                "            \"KeyInfo\": {\n" +
                "              \"X509Data\": {\n" +
                "                \"X509Certificate\": \"MIIHXjCCBUagAwIBAgIIXJO5SlW/BC8wDQYJKoZIhvcNAQELBQAwXTELMAkGA1UEBhMCQlIxEzARBgNVBAoMCklDUC1CcmFzaWwxGDAWBgNVBAsMD0FDIERJR0lUQUwgTUFJUzEfMB0GA1UEAwwWQUMgRElHSVRBTCBNVUxUSVBMQSBHMTAeFw0yNTA0MDkxODM3NDZaFw0yNjA0MDkxODM3NDZaMIHiMQswCQYDVQQGEwJCUjETMBEGA1UECgwKSUNQLUJyYXNpbDELMAkGA1UECAwCUk8xEjAQBgNVBAcMCUpJLVBBUkFOQTEfMB0GA1UECwwWQUMgRElHSVRBTCBNVUxUSVBMQSBHMTEXMBUGA1UECwwOMjQ0NDg0NDMwMDAxMDgxGTAXBgNVBAsMEHZpZGVvY29uZmVyZW5jaWExGjAYBgNVBAsMEUNlcnRpZmljYWRvIFBKIEExMSwwKgYDVQQDDCNQRlMgVFJBTlNQT1JURVMgTFREQTo1NDk4NDQ2MTAwMDE3NTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAIjjFclKU7QRcEOd93Fw9YSEWm3vDRMm7AaLBoFn7oFHeOIEs7L0jlgNt/AW892+4BrGe8QpVtQApxe+F/UumpzHk5Knkbhn/Sg1cGvHml8L9Y0A/QgzBmkbdrzmKPjJAistYaFK7eeCSmx2DYA0C+hjkXirolOK29+5Nyc1jJcykH4Kgla6lHJ/Ux18QqGor7Eh1kDBd/s6jCvcnmaduFnjayuRriWxsG3TTqXiJ8JT2cFi5IFqO5J5bgElMJu2U+Ii/x81/ALM34Ml2x7YoRHekglZtCJ10qZutd4MIUMdHSxxjYbsnTU2/OessxheDtNqy7gisD70hhrZLMRW3ZECAwEAAaOCApowggKWMIGwBgNVHREEgagwgaWgOAYFYEwBAwSgLwQtMTQwNDE5Nzk5MTc3OTk0NDE4NzAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwoCAGBWBMAQMCoBcEFVBBVUxPIEZFUk5BTkRFUyBTSUxWQaAZBgVgTAEDA6AQBA41NDk4NDQ2MTAwMDE3NaAXBgVgTAEDB6AOBAwwMDAwMDAwMDAwMDCBE1BBVUxFSVJBOEBHTUFJTC5DT00wCQYDVR0TBAIwADAfBgNVHSMEGDAWgBRsiaW2HkKBhe8dGuvXpydTNODQCDBjBgNVHSAEXDBaMFgGBmBMAQIBbDBOMEwGCCsGAQUFBwIBFkBodHRwOi8vcmVwb3NpdG9yaW8uYWNkaWdpdGFsLmNvbS5ici9kb2NzL2FjLWRpZ2l0YWwtbXVsdGlwbGEucGRmMIGgBgNVHR8EgZgwgZUwSKBGoESGQmh0dHA6Ly9yZXBvc2l0b3Jpby5hY2RpZ2l0YWwuY29tLmJyL2xjci9hYy1kaWdpdGFsLW11bHRpcGxhLWcxLmNybDBJoEegRYZDaHR0cDovL3JlcG9zaXRvcmlvMi5hY2RpZ2l0YWwuY29tLmJyL2xjci9hYy1kaWdpdGFsLW11bHRpcGxhLWcxLmNybDAOBgNVHQ8BAf8EBAMCBeAwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMEMB0GA1UdDgQWBBSkbfHqrUbuUlre82TJ/mfQL47KUDBfBggrBgEFBQcBAQRTMFEwTwYIKwYBBQUHMAKGQ2h0dHA6Ly9yZXBvc2l0b3Jpby5hY2RpZ2l0YWwuY29tLmJyL2NlcnQvYWMtZGlnaXRhbC1tdWx0aXBsYS1nMS5wN2IwDQYJKoZIhvcNAQELBQADggIBAFAW/307Sss470xVeNj5QJ/ZzXKwPRqU0hoNSc9qrHKA2OUpjWteoK4L68PYfqEk78AwiwcT8RSkU4H3jkqerkthoYDOyprU+kt9YTqKwKO4vfDwGRO9DrXO8zdJebNk4E9vgBSfsg+xSPEGd6G9Nv2vq0n4JU8qd+jh4etwBVkhjrDbIiIjdmw2HzRVBTeDfNzg0nwyCVCVgJqlKVWnLjSbFPmkAaUYiT5f5h3HHMP0eJK7ASy/HmSLJclYevIdUrh3Y3nB5vE88xl4O4C6jSXM8HAuw/MiJni4axrdYJiOxdcpsxNMa9J3LeQARo65j2zhvfEBHnxoV9u2x8HrDsnLZTmshib77VMaN3pZQXw+XnrxqNLzXlN+ENu8XnZDED0+mlmmyiA2DLN93vUVwG62xjEgKpj0pjy03TdCKYOUcj4yXsvH+ChjD8w6mUKt/4PFdR6kDrIUk6c9Si6LOqVaTcWBSMlOYn6gPSCltC3p9XRlrB3SAvEG1Z/4Czed9ucjXAeWVCEekzkv0QQKwNkS9518gjIs6AwpFbZaojJpuddtsDZYegty5mRUygJivQwuqTqxy/0DCGu9mW9z1qhr4d4d00CMRNAm1qTqEwImyN3fhE1A5DshQ1ZjpiSKTtcULTp8w0UtFKZ4+CIzgzXbf4GhO7Q7Mtguk+mmxv58\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"protCTe\": {\n" +
                "          \"@attributes\": {\n" +
                "            \"versao\": \"4.00\"\n" +
                "          },\n" +
                "          \"infProt\": {\n" +
                "            \"@attributes\": {\n" +
                "              \"Id\": \"CTe311260000962958\"\n" +
                "            },\n" +
                "            \"tpAmb\": \"1\",\n" +
                "            \"verAplic\": \"RS20260105155901\",\n" +
                "            \"chCTe\": \"11260154984461000175570010001741451001439303\",\n" +
                "            \"dhRecbto\": \"2026-01-21T09:57:42-03:00\",\n" +
                "            \"nProt\": \"311260000962958\",\n" +
                "            \"digVal\": \"BCjrv+aKf7cyJfXtraBNqzBNchs=\",\n" +
                "            \"cStat\": \"100\",\n" +
                "            \"xMotivo\": \"Autorizado o uso do CT-e\"\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }"
        val xml = jacksonObjectMapper().readTree(json.trimIndent())

        val cte = ConhecimentoTransporte(
            id = "11260154984461000175570010001741451001439303",
            xml = xml,
        )

        val duplicata = cte.getDuplicata()
        assertEquals("54984461000175", duplicata.cpfCnpjPrestador)
        assertEquals("PFS TRANSPORTES LTDA", duplicata.razaoPrestador)
        assertEquals("05925052000516", duplicata.cpfCnpjTomador)
        assertEquals("FAZENDA RIO MADEIRA S/A (GRUPO", duplicata.razaoTomador)
        assertEquals("174145", duplicata.numero)
        assertEquals(TipoDuplicata.Cte, duplicata.tipo)
        assertEquals("285.00", duplicata.valorBruto.toPlainString())
        assertEquals("285.00", duplicata.valorLiquido.toPlainString())
        assertEquals("2026-01-21T12:57:41Z", duplicata.dataEmissao.toInstant().toString())

    }

    @Test
    fun jsonRealSimplificado(){
        val json = "{\n" +
                "  \"@attributes\" : {\n" +
                "    \"versao\" : \"4.00\"\n" +
                "  },\n" +
                "  \"CTeSimp\" : {\n" +
                "    \"infCte\" : {\n" +
                "      \"@attributes\" : {\n" +
                "        \"versao\" : \"4.00\",\n" +
                "        \"Id\" : \"CTe11260205925052000516570010000012101387979859\"\n" +
                "      },\n" +
                "      \"ide\" : {\n" +
                "        \"cUF\" : \"11\",\n" +
                "        \"cCT\" : \"38797985\",\n" +
                "        \"CFOP\" : \"5352\",\n" +
                "        \"natOp\" : \"Prestacao de Servico\",\n" +
                "        \"mod\" : \"57\",\n" +
                "        \"serie\" : \"1\",\n" +
                "        \"nCT\" : \"1210\",\n" +
                "        \"dhEmi\" : \"2026-02-05T09:36:35-03:00\",\n" +
                "        \"tpImp\" : \"1\",\n" +
                "        \"tpEmis\" : \"1\",\n" +
                "        \"cDV\" : \"9\",\n" +
                "        \"tpAmb\" : \"1\",\n" +
                "        \"tpCTe\" : \"5\",\n" +
                "        \"procEmi\" : \"0\",\n" +
                "        \"verProc\" : \"1\",\n" +
                "        \"cMunEnv\" : \"1100205\",\n" +
                "        \"xMunEnv\" : \"PORTO VELHO\",\n" +
                "        \"UFEnv\" : \"RO\",\n" +
                "        \"modal\" : \"01\",\n" +
                "        \"tpServ\" : \"0\",\n" +
                "        \"UFIni\" : \"RO\",\n" +
                "        \"UFFim\" : \"RO\",\n" +
                "        \"retira\" : \"1\"\n" +
                "      },\n" +
                "      \"emit\" : {\n" +
                "        \"CNPJ\" : \"05925052000516\",\n" +
                "        \"IE\" : \"00000005470439\",\n" +
                "        \"xNome\" : \"FAZENDA RIOMADEIRA S/A-FARM-CD\",\n" +
                "        \"enderEmit\" : {\n" +
                "          \"xLgr\" : \"RODOVIA BR-364\",\n" +
                "          \"nro\" : \"9100\",\n" +
                "          \"xBairro\" : \"Loteamento Santo afonso\",\n" +
                "          \"cMun\" : \"1100205\",\n" +
                "          \"xMun\" : \"PORTO VELHO\",\n" +
                "          \"CEP\" : \"76816800\",\n" +
                "          \"UF\" : \"RO\"\n" +
                "        },\n" +
                "        \"CRT\" : \"3\"\n" +
                "      },\n" +
                "      \"toma\" : {\n" +
                "        \"toma\" : \"0\",\n" +
                "        \"indIEToma\" : \"1\",\n" +
                "        \"CNPJ\" : \"09054087000154\",\n" +
                "        \"IE\" : \"00000002304732\",\n" +
                "        \"xNome\" : \"SUSTENNUTRI NUTRICAO ANIMAL  - MATRIZ\",\n" +
                "        \"enderToma\" : {\n" +
                "          \"xLgr\" : \"DEPUTADO SERGIO CARVALHO\",\n" +
                "          \"nro\" : \"S/N\",\n" +
                "          \"xCpl\" : \"QUADRA 04 LOTE 01 LOTE 02\",\n" +
                "          \"xBairro\" : \"ZONA RURAL\",\n" +
                "          \"cMun\" : \"1100205\",\n" +
                "          \"xMun\" : \"PORTO VELHO\",\n" +
                "          \"CEP\" : \"76835500\",\n" +
                "          \"UF\" : \"RO\",\n" +
                "          \"xPais\" : \"Brasil\"\n" +
                "        },\n" +
                "        \"email\" : \"administrativoarmazem02@sustennutri.com.br\"\n" +
                "      },\n" +
                "      \"infCarga\" : {\n" +
                "        \"vCarga\" : \"1825.00\",\n" +
                "        \"proPred\" : \"OX PRIME ZP\",\n" +
                "        \"xOutCat\" : \"NUTRICAO ANIMAL\",\n" +
                "        \"infQ\" : {\n" +
                "          \"cUnid\" : \"01\",\n" +
                "          \"tpMed\" : \"02\",\n" +
                "          \"qCarga\" : \"5700.0000\"\n" +
                "        },\n" +
                "        \"vCargaAverb\" : \"1825.00\"\n" +
                "      },\n" +
                "      \"det\" : {\n" +
                "        \"@attributes\" : {\n" +
                "          \"nItem\" : \"1\"\n" +
                "        },\n" +
                "        \"cMunIni\" : \"1100205\",\n" +
                "        \"xMunIni\" : \"PORTO VELHO\",\n" +
                "        \"cMunFim\" : \"1100700\",\n" +
                "        \"xMunFim\" : \"CAMPO NOVO DE RONDONIA\",\n" +
                "        \"vPrest\" : \"1825.00\",\n" +
                "        \"vRec\" : \"1825.00\",\n" +
                "        \"infNFe\" : [ {\n" +
                "          \"chNFe\" : \"11260209054087000154550020000011301338398857\",\n" +
                "          \"dPrev\" : \"2026-02-04\"\n" +
                "        }, {\n" +
                "          \"chNFe\" : \"11260209054087000154550020000011271801923471\",\n" +
                "          \"dPrev\" : \"2026-02-04\"\n" +
                "        } ]\n" +
                "      },\n" +
                "      \"infModal\" : {\n" +
                "        \"@attributes\" : {\n" +
                "          \"versaoModal\" : \"4.00\"\n" +
                "        },\n" +
                "        \"rodo\" : {\n" +
                "          \"RNTRC\" : \"54157055\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"cobr\" : {\n" +
                "        \"fat\" : {\n" +
                "          \"vOrig\" : \"1825.00\",\n" +
                "          \"vLiq\" : \"1825.00\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"imp\" : {\n" +
                "        \"ICMS\" : {\n" +
                "          \"ICMS45\" : {\n" +
                "            \"CST\" : \"40\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"IBSCBS\" : {\n" +
                "          \"CST\" : \"000\",\n" +
                "          \"cClassTrib\" : \"000001\",\n" +
                "          \"gIBSCBS\" : {\n" +
                "            \"vBC\" : \"1825.00\",\n" +
                "            \"gIBSUF\" : {\n" +
                "              \"pIBSUF\" : \"0.10\",\n" +
                "              \"vIBSUF\" : \"1.82\"\n" +
                "            },\n" +
                "            \"gIBSMun\" : {\n" +
                "              \"pIBSMun\" : \"0.00\",\n" +
                "              \"vIBSMun\" : \"0.00\"\n" +
                "            },\n" +
                "            \"vIBS\" : \"1.82\",\n" +
                "            \"gCBS\" : {\n" +
                "              \"pCBS\" : \"0.90\",\n" +
                "              \"vCBS\" : \"16.42\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"total\" : {\n" +
                "        \"vTPrest\" : \"1825.00\",\n" +
                "        \"vTRec\" : \"1825.00\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"infCTeSupl\" : {\n" +
                "      \"qrCodCTe\" : \"https://dfe-portal.svrs.rs.gov.br/cte/qrCode?chCTe=11260205925052000516570010000012101387979859&tpAmb=1\"\n" +
                "    },\n" +
                "    \"Signature\" : {\n" +
                "      \"SignedInfo\" : {\n" +
                "        \"CanonicalizationMethod\" : {\n" +
                "          \"@attributes\" : {\n" +
                "            \"Algorithm\" : \"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"SignatureMethod\" : {\n" +
                "          \"@attributes\" : {\n" +
                "            \"Algorithm\" : \"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"Reference\" : {\n" +
                "          \"@attributes\" : {\n" +
                "            \"URI\" : \"#CTe11260205925052000516570010000012101387979859\"\n" +
                "          },\n" +
                "          \"Transforms\" : {\n" +
                "            \"Transform\" : [ {\n" +
                "              \"@attributes\" : {\n" +
                "                \"Algorithm\" : \"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"\n" +
                "              }\n" +
                "            }, {\n" +
                "              \"@attributes\" : {\n" +
                "                \"Algorithm\" : \"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"\n" +
                "              }\n" +
                "            } ]\n" +
                "          },\n" +
                "          \"DigestMethod\" : {\n" +
                "            \"@attributes\" : {\n" +
                "              \"Algorithm\" : \"http://www.w3.org/2000/09/xmldsig#sha1\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"DigestValue\" : \"QbADDV1SWJZrjmOjuSHDVvK2Yu0=\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"SignatureValue\" : \"VS87RGqw2oKCUBbGTBkhzQJkcE2bozeESNPbbsqRrezVhB6FBZTjlUI6IiFowauRk+NIQ8d/5VwUbfdMCCvQjtMaH9yQDSZKEljFFvSaYdFEA8hdzonveq3I1P1qFoqGYcurybGKWd1N/zIDVN/+/HPiY3EWtFFdaqExTSxspPCAJnP/6h4Fa/NZPMdo3hanrANr49hdLBHvt6a4mLXzdV5aLCFDYEfie4TcFdyPAkH3+XzDmq+G+uA3QldBzdJR0CgM27T+DarvsI1X1yQIaLUmC796qRhOFsqvwOqG5MVjAExkKU9bl4FYfKnmjKFZIogMS8klxIApyiqTSAjgKQ==\",\n" +
                "      \"KeyInfo\" : {\n" +
                "        \"X509Data\" : {\n" +
                "          \"X509Certificate\" : \"MIIHmDCCBYCgAwIBAgIIbjdea4TuV08wDQYJKoZIhvcNAQELBQAwczELMAkGA1UE\\nBhMCQlIxEzARBgNVBAoTCklDUC1CcmFzaWwxNjA0BgNVBAsTLVNlY3JldGFyaWEg\\nZGEgUmVjZWl0YSBGZWRlcmFsIGRvIEJyYXNpbCAtIFJGQjEXMBUGA1UEAxMOQUMg\\nTElOSyBSRkIgdjIwHhcNMjUwNzI4MTM0NTQ2WhcNMjYwNzI4MTM0NTQ2WjCB+TEL\\nMAkGA1UEBhMCQlIxEzARBgNVBAoTCklDUC1CcmFzaWwxCzAJBgNVBAgTAlJPMRQw\\nEgYDVQQHEwtQT1JUTyBWRUxITzEXMBUGA1UECxMOMjcyNzM4MDAwMDAxMzIxNjA0\\nBgNVBAsTLVNlY3JldGFyaWEgZGEgUmVjZWl0YSBGZWRlcmFsIGRvIEJyYXNpbCAt\\nIFJGQjEWMBQGA1UECxMNUkZCIGUtQ05QSiBBMTETMBEGA1UECxMKcHJlc2VuY2lh\\nbDE0MDIGA1UEAxMrRkFaRU5EQSBSSU8gTUFERUlSQSBTIEEgRkFSTTowNTkyNTA1\\nMjAwMDE5MjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJ+Qn6Cl7quD\\nZ7lAZlD560j647sGCdEfo714xemhm0oywXcZYUXvcQbvy1fuKrseINVs30iK7gkf\\n7RfKuqYh01ddM31cf5hNoOa/+OfPSWqwYCvN3Ou9Xfce5fOwngqcxOnn0OikN8vz\\ni13lbJt3rgEGYC3GKXjZey463t6n0J/H5GrxGwINhfhGbG1iZk7nHa0EALNWXV38\\n4iJPCYunugeSrIMNVmJyKTmTazWtw4uyW7ee0VVqNXrMFa8DaA31I7CTiHJuqeUv\\nAFFVDrz9VJKBxUBWbsWWDbzvBGCsVy8ySOeuo3ssHSzuy5lMJJ6F5c7zOQAfh/g/\\n0IRaWFQvCi0CAwEAAaOCAqcwggKjMB8GA1UdIwQYMBaAFA3f1kf0E07lIlgyLGam\\n5y7kV7wCMA4GA1UdDwEB/wQEAwIF4DBsBgNVHSAEZTBjMGEGBmBMAQIBOzBXMFUG\\nCCsGAQUFBwIBFklodHRwOi8vcmVwb3NpdG9yaW8ubGlua2NlcnRpZmljYWNhby5j\\nb20uYnIvYWMtbGlua3JmYi9hYy1saW5rLXJmYi1kcGMucGRmMIGwBgNVHR8Egagw\\ngaUwUKBOoEyGSmh0dHA6Ly9yZXBvc2l0b3Jpby5saW5rY2VydGlmaWNhY2FvLmNv\\nbS5ici9hYy1saW5rcmZiL2xjci1hYy1saW5rcmZidjUuY3JsMFGgT6BNhktodHRw\\nOi8vcmVwb3NpdG9yaW8yLmxpbmtjZXJ0aWZpY2FjYW8uY29tLmJyL2FjLWxpbmty\\nZmIvbGNyLWFjLWxpbmtyZmJ2NS5jcmwwYgYIKwYBBQUHAQEEVjBUMFIGCCsGAQUF\\nBzAChkZodHRwOi8vcmVwb3NpdG9yaW8ubGlua2NlcnRpZmljYWNhby5jb20uYnIv\\nYWMtbGlua3JmYi9hYy1saW5rcmZidjUucDdiMIHABgNVHREEgbgwgbWBJExFR0FM\\nSVpBQ0FPMDJAQ1NDLkdSVVBPUk9WRU1BLkNPTS5CUqAfBgVgTAEDAqAWExRWQUxE\\nRUNJUiBMVUlaIEdIRURJTqAZBgVgTAEDA6AQEw4wNTkyNTA1MjAwMDE5MqA4BgVg\\nTAEDBKAvEy0zMDA3MTk2NTMyNTM4MTQ2MjkxMDAwMDAwMDAwMDAwMDAwMDAwMDAw\\nMDAwMDCgFwYFYEwBAwegDhMMMDAwMDAwMDAwMDAwMB0GA1UdJQQWMBQGCCsGAQUF\\nBwMCBggrBgEFBQcDBDAJBgNVHRMEAjAAMA0GCSqGSIb3DQEBCwUAA4ICAQAja607\\niFZfpYX/CuLEQzPd4Vcd1j7QBYFG1Gz673lPevrYPrTe2CrA77VZNyknrpu31v7w\\n7KlrG+pe7TKa6thPL5J3zxxWFA/fFbwSWlZqvPDRZfA+RtDwEbR1xtaYmBg1el9U\\nnNgIwKr5e3CrrgHu8qMNfBxmCmf1i8Ede+Ohbi9TKmUdLP0jkEy2QOEr6SliWTcS\\nntWMvp+ZhOOcq4EfPVONFu0qCj+sjq1CIIOJiAY2pVugMrl1lG4mm/wKxHtWmgeT\\nezaF9I4KZb3FASR4IiE8fRNSehpQcPpFLYC7bS4V3Ect7O87dLLmzZMuKeir5XS1\\nJ6kDQA4MueZ19l/P1i1aUi3COj3XgKHB2ieDsjd49wM63Xi3fsqvWGoHSWEYuxp8\\nunPTwFHGY5N6OrITPD4Zy1uvROEqM3WjTnxSwg93RL0RmH5sesoaEh+p4a3dTUXc\\naH/dnUDFH++1NQfTUq5T67Yxj5XPAj2ZvYSQ7yo7hgpX5V1xwOJqAHmYCTVU4bpe\\nwWk8PRjHiwKElShHwAOSPxr+hqg6vM9l+f+AEpWWW+fzvc1XRETmAvfoxb/V7SEH\\nyLC80DvcDdOGdWx/bgtV9v5ARufey91CQazoM5YKnG3eB+f1LEUI+qkLmEaDo+AR\\nIsBUjC42kVFCM5oFsOpeEwEZml/UBJc6fRH8Wg==\\n\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"protCTe\" : {\n" +
                "    \"@attributes\" : {\n" +
                "      \"versao\" : \"4.00\"\n" +
                "    },\n" +
                "    \"infProt\" : {\n" +
                "      \"@attributes\" : {\n" +
                "        \"Id\" : \"CTe311260001992740\"\n" +
                "      },\n" +
                "      \"tpAmb\" : \"1\",\n" +
                "      \"verAplic\" : \"RS20260105155901\",\n" +
                "      \"chCTe\" : \"11260205925052000516570010000012101387979859\",\n" +
                "      \"dhRecbto\" : \"2026-02-05T09:50:54-03:00\",\n" +
                "      \"nProt\" : \"311260001992740\",\n" +
                "      \"digVal\" : \"QbADDV1SWJZrjmOjuSHDVvK2Yu0=\",\n" +
                "      \"cStat\" : \"100\",\n" +
                "      \"xMotivo\" : \"Autorizado o uso do CT-e\"\n" +
                "    }\n" +
                "  }\n" +
                "}"
    }

    @Test
    fun jsonToma(){
        val jsonToma = "{\n" +
                "  \"@attributes\" : {\n" +
                "    \"versao\" : \"4.00\"\n" +
                "  },\n" +
                "  \"CTeSimp\" : {\n" +
                "    \"infCte\" : {\n" +
                "      \"@attributes\" : {\n" +
                "        \"versao\" : \"4.00\",\n" +
                "        \"Id\" : \"CTe11260205925052000516570010000012101387979859\"\n" +
                "      },\n" +
                "      \"ide\" : {\n" +
                "        \"cUF\" : \"11\",\n" +
                "        \"cCT\" : \"38797985\",\n" +
                "        \"CFOP\" : \"5352\",\n" +
                "        \"natOp\" : \"Prestacao de Servico\",\n" +
                "        \"mod\" : \"57\",\n" +
                "        \"serie\" : \"1\",\n" +
                "        \"nCT\" : \"1210\",\n" +
                "        \"dhEmi\" : \"2026-02-05T09:36:35-03:00\",\n" +
                "        \"tpImp\" : \"1\",\n" +
                "        \"tpEmis\" : \"1\",\n" +
                "        \"cDV\" : \"9\",\n" +
                "        \"tpAmb\" : \"1\",\n" +
                "        \"tpCTe\" : \"5\",\n" +
                "        \"procEmi\" : \"0\",\n" +
                "        \"verProc\" : \"1\",\n" +
                "        \"cMunEnv\" : \"1100205\",\n" +
                "        \"xMunEnv\" : \"PORTO VELHO\",\n" +
                "        \"UFEnv\" : \"RO\",\n" +
                "        \"modal\" : \"01\",\n" +
                "        \"tpServ\" : \"0\",\n" +
                "        \"UFIni\" : \"RO\",\n" +
                "        \"UFFim\" : \"RO\",\n" +
                "        \"retira\" : \"1\"\n" +
                "      },\n" +
                "      \"emit\" : {\n" +
                "        \"CNPJ\" : \"05925052000516\",\n" +
                "        \"IE\" : \"00000005470439\",\n" +
                "        \"xNome\" : \"FAZENDA RIOMADEIRA S/A-FARM-CD\",\n" +
                "        \"enderEmit\" : {\n" +
                "          \"xLgr\" : \"RODOVIA BR-364\",\n" +
                "          \"nro\" : \"9100\",\n" +
                "          \"xBairro\" : \"Loteamento Santo afonso\",\n" +
                "          \"cMun\" : \"1100205\",\n" +
                "          \"xMun\" : \"PORTO VELHO\",\n" +
                "          \"CEP\" : \"76816800\",\n" +
                "          \"UF\" : \"RO\"\n" +
                "        },\n" +
                "        \"CRT\" : \"3\"\n" +
                "      },\n" +
                "      \"toma\" : {\n" +
                "        \"toma\" : \"0\",\n" +
                "        \"indIEToma\" : \"1\",\n" +
                "        \"CNPJ\" : \"09054087000154\",\n" +
                "        \"IE\" : \"00000002304732\",\n" +
                "        \"xNome\" : \"SUSTENNUTRI NUTRICAO ANIMAL  - MATRIZ\",\n" +
                "        \"enderToma\" : {\n" +
                "          \"xLgr\" : \"DEPUTADO SERGIO CARVALHO\",\n" +
                "          \"nro\" : \"S/N\",\n" +
                "          \"xCpl\" : \"QUADRA 04 LOTE 01 LOTE 02\",\n" +
                "          \"xBairro\" : \"ZONA RURAL\",\n" +
                "          \"cMun\" : \"1100205\",\n" +
                "          \"xMun\" : \"PORTO VELHO\",\n" +
                "          \"CEP\" : \"76835500\",\n" +
                "          \"UF\" : \"RO\",\n" +
                "          \"xPais\" : \"Brasil\"\n" +
                "        },\n" +
                "        \"email\" : \"administrativoarmazem02@sustennutri.com.br\"\n" +
                "      },\n" +
                "      \"infCarga\" : {\n" +
                "        \"vCarga\" : \"1825.00\",\n" +
                "        \"proPred\" : \"OX PRIME ZP\",\n" +
                "        \"xOutCat\" : \"NUTRICAO ANIMAL\",\n" +
                "        \"infQ\" : {\n" +
                "          \"cUnid\" : \"01\",\n" +
                "          \"tpMed\" : \"02\",\n" +
                "          \"qCarga\" : \"5700.0000\"\n" +
                "        },\n" +
                "        \"vCargaAverb\" : \"1825.00\"\n" +
                "      },\n" +
                "      \"det\" : {\n" +
                "        \"@attributes\" : {\n" +
                "          \"nItem\" : \"1\"\n" +
                "        },\n" +
                "        \"cMunIni\" : \"1100205\",\n" +
                "        \"xMunIni\" : \"PORTO VELHO\",\n" +
                "        \"cMunFim\" : \"1100700\",\n" +
                "        \"xMunFim\" : \"CAMPO NOVO DE RONDONIA\",\n" +
                "        \"vPrest\" : \"1825.00\",\n" +
                "        \"vRec\" : \"1825.00\",\n" +
                "        \"infNFe\" : [ {\n" +
                "          \"chNFe\" : \"11260209054087000154550020000011301338398857\",\n" +
                "          \"dPrev\" : \"2026-02-04\"\n" +
                "        }, {\n" +
                "          \"chNFe\" : \"11260209054087000154550020000011271801923471\",\n" +
                "          \"dPrev\" : \"2026-02-04\"\n" +
                "        } ]\n" +
                "      },\n" +
                "      \"infModal\" : {\n" +
                "        \"@attributes\" : {\n" +
                "          \"versaoModal\" : \"4.00\"\n" +
                "        },\n" +
                "        \"rodo\" : {\n" +
                "          \"RNTRC\" : \"54157055\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"cobr\" : {\n" +
                "        \"fat\" : {\n" +
                "          \"vOrig\" : \"1825.00\",\n" +
                "          \"vLiq\" : \"1825.00\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"imp\" : {\n" +
                "        \"ICMS\" : {\n" +
                "          \"ICMS45\" : {\n" +
                "            \"CST\" : \"40\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"IBSCBS\" : {\n" +
                "          \"CST\" : \"000\",\n" +
                "          \"cClassTrib\" : \"000001\",\n" +
                "          \"gIBSCBS\" : {\n" +
                "            \"vBC\" : \"1825.00\",\n" +
                "            \"gIBSUF\" : {\n" +
                "              \"pIBSUF\" : \"0.10\",\n" +
                "              \"vIBSUF\" : \"1.82\"\n" +
                "            },\n" +
                "            \"gIBSMun\" : {\n" +
                "              \"pIBSMun\" : \"0.00\",\n" +
                "              \"vIBSMun\" : \"0.00\"\n" +
                "            },\n" +
                "            \"vIBS\" : \"1.82\",\n" +
                "            \"gCBS\" : {\n" +
                "              \"pCBS\" : \"0.90\",\n" +
                "              \"vCBS\" : \"16.42\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"total\" : {\n" +
                "        \"vTPrest\" : \"1825.00\",\n" +
                "        \"vTRec\" : \"1825.00\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"infCTeSupl\" : {\n" +
                "      \"qrCodCTe\" : \"https://dfe-portal.svrs.rs.gov.br/cte/qrCode?chCTe=11260205925052000516570010000012101387979859&tpAmb=1\"\n" +
                "    },\n" +
                "    \"Signature\" : {\n" +
                "      \"SignedInfo\" : {\n" +
                "        \"CanonicalizationMethod\" : {\n" +
                "          \"@attributes\" : {\n" +
                "            \"Algorithm\" : \"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"SignatureMethod\" : {\n" +
                "          \"@attributes\" : {\n" +
                "            \"Algorithm\" : \"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"Reference\" : {\n" +
                "          \"@attributes\" : {\n" +
                "            \"URI\" : \"#CTe11260205925052000516570010000012101387979859\"\n" +
                "          },\n" +
                "          \"Transforms\" : {\n" +
                "            \"Transform\" : [ {\n" +
                "              \"@attributes\" : {\n" +
                "                \"Algorithm\" : \"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"\n" +
                "              }\n" +
                "            }, {\n" +
                "              \"@attributes\" : {\n" +
                "                \"Algorithm\" : \"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"\n" +
                "              }\n" +
                "            } ]\n" +
                "          },\n" +
                "          \"DigestMethod\" : {\n" +
                "            \"@attributes\" : {\n" +
                "              \"Algorithm\" : \"http://www.w3.org/2000/09/xmldsig#sha1\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"DigestValue\" : \"QbADDV1SWJZrjmOjuSHDVvK2Yu0=\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"SignatureValue\" : \"VS87RGqw2oKCUBbGTBkhzQJkcE2bozeESNPbbsqRrezVhB6FBZTjlUI6IiFowauRk+NIQ8d/5VwUbfdMCCvQjtMaH9yQDSZKEljFFvSaYdFEA8hdzonveq3I1P1qFoqGYcurybGKWd1N/zIDVN/+/HPiY3EWtFFdaqExTSxspPCAJnP/6h4Fa/NZPMdo3hanrANr49hdLBHvt6a4mLXzdV5aLCFDYEfie4TcFdyPAkH3+XzDmq+G+uA3QldBzdJR0CgM27T+DarvsI1X1yQIaLUmC796qRhOFsqvwOqG5MVjAExkKU9bl4FYfKnmjKFZIogMS8klxIApyiqTSAjgKQ==\",\n" +
                "      \"KeyInfo\" : {\n" +
                "        \"X509Data\" : {\n" +
                "          \"X509Certificate\" : \"MIIHmDCCBYCgAwIBAgIIbjdea4TuV08wDQYJKoZIhvcNAQELBQAwczELMAkGA1UE\\nBhMCQlIxEzARBgNVBAoTCklDUC1CcmFzaWwxNjA0BgNVBAsTLVNlY3JldGFyaWEg\\nZGEgUmVjZWl0YSBGZWRlcmFsIGRvIEJyYXNpbCAtIFJGQjEXMBUGA1UEAxMOQUMg\\nTElOSyBSRkIgdjIwHhcNMjUwNzI4MTM0NTQ2WhcNMjYwNzI4MTM0NTQ2WjCB+TEL\\nMAkGA1UEBhMCQlIxEzARBgNVBAoTCklDUC1CcmFzaWwxCzAJBgNVBAgTAlJPMRQw\\nEgYDVQQHEwtQT1JUTyBWRUxITzEXMBUGA1UECxMOMjcyNzM4MDAwMDAxMzIxNjA0\\nBgNVBAsTLVNlY3JldGFyaWEgZGEgUmVjZWl0YSBGZWRlcmFsIGRvIEJyYXNpbCAt\\nIFJGQjEWMBQGA1UECxMNUkZCIGUtQ05QSiBBMTETMBEGA1UECxMKcHJlc2VuY2lh\\nbDE0MDIGA1UEAxMrRkFaRU5EQSBSSU8gTUFERUlSQSBTIEEgRkFSTTowNTkyNTA1\\nMjAwMDE5MjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJ+Qn6Cl7quD\\nZ7lAZlD560j647sGCdEfo714xemhm0oywXcZYUXvcQbvy1fuKrseINVs30iK7gkf\\n7RfKuqYh01ddM31cf5hNoOa/+OfPSWqwYCvN3Ou9Xfce5fOwngqcxOnn0OikN8vz\\ni13lbJt3rgEGYC3GKXjZey463t6n0J/H5GrxGwINhfhGbG1iZk7nHa0EALNWXV38\\n4iJPCYunugeSrIMNVmJyKTmTazWtw4uyW7ee0VVqNXrMFa8DaA31I7CTiHJuqeUv\\nAFFVDrz9VJKBxUBWbsWWDbzvBGCsVy8ySOeuo3ssHSzuy5lMJJ6F5c7zOQAfh/g/\\n0IRaWFQvCi0CAwEAAaOCAqcwggKjMB8GA1UdIwQYMBaAFA3f1kf0E07lIlgyLGam\\n5y7kV7wCMA4GA1UdDwEB/wQEAwIF4DBsBgNVHSAEZTBjMGEGBmBMAQIBOzBXMFUG\\nCCsGAQUFBwIBFklodHRwOi8vcmVwb3NpdG9yaW8ubGlua2NlcnRpZmljYWNhby5j\\nb20uYnIvYWMtbGlua3JmYi9hYy1saW5rLXJmYi1kcGMucGRmMIGwBgNVHR8Egagw\\ngaUwUKBOoEyGSmh0dHA6Ly9yZXBvc2l0b3Jpby5saW5rY2VydGlmaWNhY2FvLmNv\\nbS5ici9hYy1saW5rcmZiL2xjci1hYy1saW5rcmZidjUuY3JsMFGgT6BNhktodHRw\\nOi8vcmVwb3NpdG9yaW8yLmxpbmtjZXJ0aWZpY2FjYW8uY29tLmJyL2FjLWxpbmty\\nZmIvbGNyLWFjLWxpbmtyZmJ2NS5jcmwwYgYIKwYBBQUHAQEEVjBUMFIGCCsGAQUF\\nBzAChkZodHRwOi8vcmVwb3NpdG9yaW8ubGlua2NlcnRpZmljYWNhby5jb20uYnIv\\nYWMtbGlua3JmYi9hYy1saW5rcmZidjUucDdiMIHABgNVHREEgbgwgbWBJExFR0FM\\nSVpBQ0FPMDJAQ1NDLkdSVVBPUk9WRU1BLkNPTS5CUqAfBgVgTAEDAqAWExRWQUxE\\nRUNJUiBMVUlaIEdIRURJTqAZBgVgTAEDA6AQEw4wNTkyNTA1MjAwMDE5MqA4BgVg\\nTAEDBKAvEy0zMDA3MTk2NTMyNTM4MTQ2MjkxMDAwMDAwMDAwMDAwMDAwMDAwMDAw\\nMDAwMDCgFwYFYEwBAwegDhMMMDAwMDAwMDAwMDAwMB0GA1UdJQQWMBQGCCsGAQUF\\nBwMCBggrBgEFBQcDBDAJBgNVHRMEAjAAMA0GCSqGSIb3DQEBCwUAA4ICAQAja607\\niFZfpYX/CuLEQzPd4Vcd1j7QBYFG1Gz673lPevrYPrTe2CrA77VZNyknrpu31v7w\\n7KlrG+pe7TKa6thPL5J3zxxWFA/fFbwSWlZqvPDRZfA+RtDwEbR1xtaYmBg1el9U\\nnNgIwKr5e3CrrgHu8qMNfBxmCmf1i8Ede+Ohbi9TKmUdLP0jkEy2QOEr6SliWTcS\\nntWMvp+ZhOOcq4EfPVONFu0qCj+sjq1CIIOJiAY2pVugMrl1lG4mm/wKxHtWmgeT\\nezaF9I4KZb3FASR4IiE8fRNSehpQcPpFLYC7bS4V3Ect7O87dLLmzZMuKeir5XS1\\nJ6kDQA4MueZ19l/P1i1aUi3COj3XgKHB2ieDsjd49wM63Xi3fsqvWGoHSWEYuxp8\\nunPTwFHGY5N6OrITPD4Zy1uvROEqM3WjTnxSwg93RL0RmH5sesoaEh+p4a3dTUXc\\naH/dnUDFH++1NQfTUq5T67Yxj5XPAj2ZvYSQ7yo7hgpX5V1xwOJqAHmYCTVU4bpe\\nwWk8PRjHiwKElShHwAOSPxr+hqg6vM9l+f+AEpWWW+fzvc1XRETmAvfoxb/V7SEH\\nyLC80DvcDdOGdWx/bgtV9v5ARufey91CQazoM5YKnG3eB+f1LEUI+qkLmEaDo+AR\\nIsBUjC42kVFCM5oFsOpeEwEZml/UBJc6fRH8Wg==\\n\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"protCTe\" : {\n" +
                "    \"@attributes\" : {\n" +
                "      \"versao\" : \"4.00\"\n" +
                "    },\n" +
                "    \"infProt\" : {\n" +
                "      \"@attributes\" : {\n" +
                "        \"Id\" : \"CTe311260001992740\"\n" +
                "      },\n" +
                "      \"tpAmb\" : \"1\",\n" +
                "      \"verAplic\" : \"RS20260105155901\",\n" +
                "      \"chCTe\" : \"11260205925052000516570010000012101387979859\",\n" +
                "      \"dhRecbto\" : \"2026-02-05T09:50:54-03:00\",\n" +
                "      \"nProt\" : \"311260001992740\",\n" +
                "      \"digVal\" : \"QbADDV1SWJZrjmOjuSHDVvK2Yu0=\",\n" +
                "      \"cStat\" : \"100\",\n" +
                "      \"xMotivo\" : \"Autorizado o uso do CT-e\"\n" +
                "    }\n" +
                "  }\n" +
                "}"

        val xml = jacksonObjectMapper().readTree(jsonToma.trimIndent())
        val cte = ConhecimentoTransporte(
            id = "11260205925052000516570010000012101387979859",
            xml = xml,
        )

        assertEquals("09054087000154", cte.getCpfCnpjTomador())
        assertEquals("SUSTENNUTRI NUTRICAO ANIMAL  - MATRIZ", cte.getNomeTomador())
    }

    @Test
    fun testeValorTotal(){
        val json = "{\n" +
                "  \"@attributes\" : {\n" +
                "    \"versao\" : \"4.00\"\n" +
                "  },\n" +
                "  \"CTeSimp\" : {\n" +
                "    \"infCte\" : {\n" +
                "      \"@attributes\" : {\n" +
                "        \"versao\" : \"4.00\",\n" +
                "        \"Id\" : \"CTe11260205925052000516570010000012101387979859\"\n" +
                "      },\n" +
                "      \"ide\" : {\n" +
                "        \"cUF\" : \"11\",\n" +
                "        \"cCT\" : \"38797985\",\n" +
                "        \"CFOP\" : \"5352\",\n" +
                "        \"natOp\" : \"Prestacao de Servico\",\n" +
                "        \"mod\" : \"57\",\n" +
                "        \"serie\" : \"1\",\n" +
                "        \"nCT\" : \"1210\",\n" +
                "        \"dhEmi\" : \"2026-02-05T09:36:35-03:00\",\n" +
                "        \"tpImp\" : \"1\",\n" +
                "        \"tpEmis\" : \"1\",\n" +
                "        \"cDV\" : \"9\",\n" +
                "        \"tpAmb\" : \"1\",\n" +
                "        \"tpCTe\" : \"5\",\n" +
                "        \"procEmi\" : \"0\",\n" +
                "        \"verProc\" : \"1\",\n" +
                "        \"cMunEnv\" : \"1100205\",\n" +
                "        \"xMunEnv\" : \"PORTO VELHO\",\n" +
                "        \"UFEnv\" : \"RO\",\n" +
                "        \"modal\" : \"01\",\n" +
                "        \"tpServ\" : \"0\",\n" +
                "        \"UFIni\" : \"RO\",\n" +
                "        \"UFFim\" : \"RO\",\n" +
                "        \"retira\" : \"1\"\n" +
                "      },\n" +
                "      \"emit\" : {\n" +
                "        \"CNPJ\" : \"05925052000516\",\n" +
                "        \"IE\" : \"00000005470439\",\n" +
                "        \"xNome\" : \"FAZENDA RIOMADEIRA S/A-FARM-CD\",\n" +
                "        \"enderEmit\" : {\n" +
                "          \"xLgr\" : \"RODOVIA BR-364\",\n" +
                "          \"nro\" : \"9100\",\n" +
                "          \"xBairro\" : \"Loteamento Santo afonso\",\n" +
                "          \"cMun\" : \"1100205\",\n" +
                "          \"xMun\" : \"PORTO VELHO\",\n" +
                "          \"CEP\" : \"76816800\",\n" +
                "          \"UF\" : \"RO\"\n" +
                "        },\n" +
                "        \"CRT\" : \"3\"\n" +
                "      },\n" +
                "      \"toma\" : {\n" +
                "        \"toma\" : \"0\",\n" +
                "        \"indIEToma\" : \"1\",\n" +
                "        \"CNPJ\" : \"09054087000154\",\n" +
                "        \"IE\" : \"00000002304732\",\n" +
                "        \"xNome\" : \"SUSTENNUTRI NUTRICAO ANIMAL  - MATRIZ\",\n" +
                "        \"enderToma\" : {\n" +
                "          \"xLgr\" : \"DEPUTADO SERGIO CARVALHO\",\n" +
                "          \"nro\" : \"S/N\",\n" +
                "          \"xCpl\" : \"QUADRA 04 LOTE 01 LOTE 02\",\n" +
                "          \"xBairro\" : \"ZONA RURAL\",\n" +
                "          \"cMun\" : \"1100205\",\n" +
                "          \"xMun\" : \"PORTO VELHO\",\n" +
                "          \"CEP\" : \"76835500\",\n" +
                "          \"UF\" : \"RO\",\n" +
                "          \"xPais\" : \"Brasil\"\n" +
                "        },\n" +
                "        \"email\" : \"administrativoarmazem02@sustennutri.com.br\"\n" +
                "      },\n" +
                "      \"infCarga\" : {\n" +
                "        \"vCarga\" : \"1825.00\",\n" +
                "        \"proPred\" : \"OX PRIME ZP\",\n" +
                "        \"xOutCat\" : \"NUTRICAO ANIMAL\",\n" +
                "        \"infQ\" : {\n" +
                "          \"cUnid\" : \"01\",\n" +
                "          \"tpMed\" : \"02\",\n" +
                "          \"qCarga\" : \"5700.0000\"\n" +
                "        },\n" +
                "        \"vCargaAverb\" : \"1825.00\"\n" +
                "      },\n" +
                "      \"det\" : {\n" +
                "        \"@attributes\" : {\n" +
                "          \"nItem\" : \"1\"\n" +
                "        },\n" +
                "        \"cMunIni\" : \"1100205\",\n" +
                "        \"xMunIni\" : \"PORTO VELHO\",\n" +
                "        \"cMunFim\" : \"1100700\",\n" +
                "        \"xMunFim\" : \"CAMPO NOVO DE RONDONIA\",\n" +
                "        \"vPrest\" : \"1825.00\",\n" +
                "        \"vRec\" : \"1825.00\",\n" +
                "        \"infNFe\" : [ {\n" +
                "          \"chNFe\" : \"11260209054087000154550020000011301338398857\",\n" +
                "          \"dPrev\" : \"2026-02-04\"\n" +
                "        }, {\n" +
                "          \"chNFe\" : \"11260209054087000154550020000011271801923471\",\n" +
                "          \"dPrev\" : \"2026-02-04\"\n" +
                "        } ]\n" +
                "      },\n" +
                "      \"infModal\" : {\n" +
                "        \"@attributes\" : {\n" +
                "          \"versaoModal\" : \"4.00\"\n" +
                "        },\n" +
                "        \"rodo\" : {\n" +
                "          \"RNTRC\" : \"54157055\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"cobr\" : {\n" +
                "        \"fat\" : {\n" +
                "          \"vOrig\" : \"1825.00\",\n" +
                "          \"vLiq\" : \"1825.00\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"imp\" : {\n" +
                "        \"ICMS\" : {\n" +
                "          \"ICMS45\" : {\n" +
                "            \"CST\" : \"40\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"IBSCBS\" : {\n" +
                "          \"CST\" : \"000\",\n" +
                "          \"cClassTrib\" : \"000001\",\n" +
                "          \"gIBSCBS\" : {\n" +
                "            \"vBC\" : \"1825.00\",\n" +
                "            \"gIBSUF\" : {\n" +
                "              \"pIBSUF\" : \"0.10\",\n" +
                "              \"vIBSUF\" : \"1.82\"\n" +
                "            },\n" +
                "            \"gIBSMun\" : {\n" +
                "              \"pIBSMun\" : \"0.00\",\n" +
                "              \"vIBSMun\" : \"0.00\"\n" +
                "            },\n" +
                "            \"vIBS\" : \"1.82\",\n" +
                "            \"gCBS\" : {\n" +
                "              \"pCBS\" : \"0.90\",\n" +
                "              \"vCBS\" : \"16.42\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"total\" : {\n" +
                "        \"vTPrest\" : \"1825.00\",\n" +
                "        \"vTRec\" : \"1825.00\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"infCTeSupl\" : {\n" +
                "      \"qrCodCTe\" : \"https://dfe-portal.svrs.rs.gov.br/cte/qrCode?chCTe=11260205925052000516570010000012101387979859&tpAmb=1\"\n" +
                "    },\n" +
                "    \"Signature\" : {\n" +
                "      \"SignedInfo\" : {\n" +
                "        \"CanonicalizationMethod\" : {\n" +
                "          \"@attributes\" : {\n" +
                "            \"Algorithm\" : \"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"SignatureMethod\" : {\n" +
                "          \"@attributes\" : {\n" +
                "            \"Algorithm\" : \"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"Reference\" : {\n" +
                "          \"@attributes\" : {\n" +
                "            \"URI\" : \"#CTe11260205925052000516570010000012101387979859\"\n" +
                "          },\n" +
                "          \"Transforms\" : {\n" +
                "            \"Transform\" : [ {\n" +
                "              \"@attributes\" : {\n" +
                "                \"Algorithm\" : \"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"\n" +
                "              }\n" +
                "            }, {\n" +
                "              \"@attributes\" : {\n" +
                "                \"Algorithm\" : \"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"\n" +
                "              }\n" +
                "            } ]\n" +
                "          },\n" +
                "          \"DigestMethod\" : {\n" +
                "            \"@attributes\" : {\n" +
                "              \"Algorithm\" : \"http://www.w3.org/2000/09/xmldsig#sha1\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"DigestValue\" : \"QbADDV1SWJZrjmOjuSHDVvK2Yu0=\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"SignatureValue\" : \"VS87RGqw2oKCUBbGTBkhzQJkcE2bozeESNPbbsqRrezVhB6FBZTjlUI6IiFowauRk+NIQ8d/5VwUbfdMCCvQjtMaH9yQDSZKEljFFvSaYdFEA8hdzonveq3I1P1qFoqGYcurybGKWd1N/zIDVN/+/HPiY3EWtFFdaqExTSxspPCAJnP/6h4Fa/NZPMdo3hanrANr49hdLBHvt6a4mLXzdV5aLCFDYEfie4TcFdyPAkH3+XzDmq+G+uA3QldBzdJR0CgM27T+DarvsI1X1yQIaLUmC796qRhOFsqvwOqG5MVjAExkKU9bl4FYfKnmjKFZIogMS8klxIApyiqTSAjgKQ==\",\n" +
                "      \"KeyInfo\" : {\n" +
                "        \"X509Data\" : {\n" +
                "          \"X509Certificate\" : \"MIIHmDCCBYCgAwIBAgIIbjdea4TuV08wDQYJKoZIhvcNAQELBQAwczELMAkGA1UE\\nBhMCQlIxEzARBgNVBAoTCklDUC1CcmFzaWwxNjA0BgNVBAsTLVNlY3JldGFyaWEg\\nZGEgUmVjZWl0YSBGZWRlcmFsIGRvIEJyYXNpbCAtIFJGQjEXMBUGA1UEAxMOQUMg\\nTElOSyBSRkIgdjIwHhcNMjUwNzI4MTM0NTQ2WhcNMjYwNzI4MTM0NTQ2WjCB+TEL\\nMAkGA1UEBhMCQlIxEzARBgNVBAoTCklDUC1CcmFzaWwxCzAJBgNVBAgTAlJPMRQw\\nEgYDVQQHEwtQT1JUTyBWRUxITzEXMBUGA1UECxMOMjcyNzM4MDAwMDAxMzIxNjA0\\nBgNVBAsTLVNlY3JldGFyaWEgZGEgUmVjZWl0YSBGZWRlcmFsIGRvIEJyYXNpbCAt\\nIFJGQjEWMBQGA1UECxMNUkZCIGUtQ05QSiBBMTETMBEGA1UECxMKcHJlc2VuY2lh\\nbDE0MDIGA1UEAxMrRkFaRU5EQSBSSU8gTUFERUlSQSBTIEEgRkFSTTowNTkyNTA1\\nMjAwMDE5MjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJ+Qn6Cl7quD\\nZ7lAZlD560j647sGCdEfo714xemhm0oywXcZYUXvcQbvy1fuKrseINVs30iK7gkf\\n7RfKuqYh01ddM31cf5hNoOa/+OfPSWqwYCvN3Ou9Xfce5fOwngqcxOnn0OikN8vz\\ni13lbJt3rgEGYC3GKXjZey463t6n0J/H5GrxGwINhfhGbG1iZk7nHa0EALNWXV38\\n4iJPCYunugeSrIMNVmJyKTmTazWtw4uyW7ee0VVqNXrMFa8DaA31I7CTiHJuqeUv\\nAFFVDrz9VJKBxUBWbsWWDbzvBGCsVy8ySOeuo3ssHSzuy5lMJJ6F5c7zOQAfh/g/\\n0IRaWFQvCi0CAwEAAaOCAqcwggKjMB8GA1UdIwQYMBaAFA3f1kf0E07lIlgyLGam\\n5y7kV7wCMA4GA1UdDwEB/wQEAwIF4DBsBgNVHSAEZTBjMGEGBmBMAQIBOzBXMFUG\\nCCsGAQUFBwIBFklodHRwOi8vcmVwb3NpdG9yaW8ubGlua2NlcnRpZmljYWNhby5j\\nb20uYnIvYWMtbGlua3JmYi9hYy1saW5rLXJmYi1kcGMucGRmMIGwBgNVHR8Egagw\\ngaUwUKBOoEyGSmh0dHA6Ly9yZXBvc2l0b3Jpby5saW5rY2VydGlmaWNhY2FvLmNv\\nbS5ici9hYy1saW5rcmZiL2xjci1hYy1saW5rcmZidjUuY3JsMFGgT6BNhktodHRw\\nOi8vcmVwb3NpdG9yaW8yLmxpbmtjZXJ0aWZpY2FjYW8uY29tLmJyL2FjLWxpbmty\\nZmIvbGNyLWFjLWxpbmtyZmJ2NS5jcmwwYgYIKwYBBQUHAQEEVjBUMFIGCCsGAQUF\\nBzAChkZodHRwOi8vcmVwb3NpdG9yaW8ubGlua2NlcnRpZmljYWNhby5jb20uYnIv\\nYWMtbGlua3JmYi9hYy1saW5rcmZidjUucDdiMIHABgNVHREEgbgwgbWBJExFR0FM\\nSVpBQ0FPMDJAQ1NDLkdSVVBPUk9WRU1BLkNPTS5CUqAfBgVgTAEDAqAWExRWQUxE\\nRUNJUiBMVUlaIEdIRURJTqAZBgVgTAEDA6AQEw4wNTkyNTA1MjAwMDE5MqA4BgVg\\nTAEDBKAvEy0zMDA3MTk2NTMyNTM4MTQ2MjkxMDAwMDAwMDAwMDAwMDAwMDAwMDAw\\nMDAwMDCgFwYFYEwBAwegDhMMMDAwMDAwMDAwMDAwMB0GA1UdJQQWMBQGCCsGAQUF\\nBwMCBggrBgEFBQcDBDAJBgNVHRMEAjAAMA0GCSqGSIb3DQEBCwUAA4ICAQAja607\\niFZfpYX/CuLEQzPd4Vcd1j7QBYFG1Gz673lPevrYPrTe2CrA77VZNyknrpu31v7w\\n7KlrG+pe7TKa6thPL5J3zxxWFA/fFbwSWlZqvPDRZfA+RtDwEbR1xtaYmBg1el9U\\nnNgIwKr5e3CrrgHu8qMNfBxmCmf1i8Ede+Ohbi9TKmUdLP0jkEy2QOEr6SliWTcS\\nntWMvp+ZhOOcq4EfPVONFu0qCj+sjq1CIIOJiAY2pVugMrl1lG4mm/wKxHtWmgeT\\nezaF9I4KZb3FASR4IiE8fRNSehpQcPpFLYC7bS4V3Ect7O87dLLmzZMuKeir5XS1\\nJ6kDQA4MueZ19l/P1i1aUi3COj3XgKHB2ieDsjd49wM63Xi3fsqvWGoHSWEYuxp8\\nunPTwFHGY5N6OrITPD4Zy1uvROEqM3WjTnxSwg93RL0RmH5sesoaEh+p4a3dTUXc\\naH/dnUDFH++1NQfTUq5T67Yxj5XPAj2ZvYSQ7yo7hgpX5V1xwOJqAHmYCTVU4bpe\\nwWk8PRjHiwKElShHwAOSPxr+hqg6vM9l+f+AEpWWW+fzvc1XRETmAvfoxb/V7SEH\\nyLC80DvcDdOGdWx/bgtV9v5ARufey91CQazoM5YKnG3eB+f1LEUI+qkLmEaDo+AR\\nIsBUjC42kVFCM5oFsOpeEwEZml/UBJc6fRH8Wg==\\n\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"protCTe\" : {\n" +
                "    \"@attributes\" : {\n" +
                "      \"versao\" : \"4.00\"\n" +
                "    },\n" +
                "    \"infProt\" : {\n" +
                "      \"@attributes\" : {\n" +
                "        \"Id\" : \"CTe311260001992740\"\n" +
                "      },\n" +
                "      \"tpAmb\" : \"1\",\n" +
                "      \"verAplic\" : \"RS20260105155901\",\n" +
                "      \"chCTe\" : \"11260205925052000516570010000012101387979859\",\n" +
                "      \"dhRecbto\" : \"2026-02-05T09:50:54-03:00\",\n" +
                "      \"nProt\" : \"311260001992740\",\n" +
                "      \"digVal\" : \"QbADDV1SWJZrjmOjuSHDVvK2Yu0=\",\n" +
                "      \"cStat\" : \"100\",\n" +
                "      \"xMotivo\" : \"Autorizado o uso do CT-e\"\n" +
                "    }\n" +
                "  }\n" +
                "}"

        val xml = jacksonObjectMapper().readTree(json.trimIndent())
        val cte = ConhecimentoTransporte(
            id = "11260205925052000516570010000012101387979859",
            xml = xml,
        )

        assertEquals("1825.00", cte.getValorBruto()?.toPlainString())
        assertEquals("1825.00", cte.getValorLiquido()?.toPlainString())
    }
}
