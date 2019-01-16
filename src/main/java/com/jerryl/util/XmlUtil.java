package com.jerryl.util;

import javax.xml.stream.XMLInputFactory;

public class XmlUtil
{
  public static XMLInputFactory createSafeXmlInputFactory()
  {
    XMLInputFactory xif = XMLInputFactory.newInstance();
    if (xif.isPropertySupported("javax.xml.stream.isReplacingEntityReferences")) {
      xif.setProperty("javax.xml.stream.isReplacingEntityReferences", Boolean.valueOf(false));
    }
    if (xif.isPropertySupported("javax.xml.stream.isSupportingExternalEntities")) {
      xif.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.valueOf(false));
    }
    if (xif.isPropertySupported("javax.xml.stream.supportDTD")) {
      xif.setProperty("javax.xml.stream.supportDTD", Boolean.valueOf(false));
    }
    return xif;
  }
}